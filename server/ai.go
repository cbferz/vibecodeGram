package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5"
)

const hfChatURL = "https://router.huggingface.co/v1/chat/completions"

var hfHTTPClient = &http.Client{Timeout: 75 * time.Second}

type aiMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type aiChatInput struct {
	Messages   []aiMessage `json:"messages"`
	Model      string      `json:"model,omitempty"`
	Temperature float64    `json:"temperature,omitempty"`
	MaxTokens  int         `json:"max_tokens,omitempty"`
}

type hfChatResponse struct {
	Choices []struct {
		Message aiMessage `json:"message"`
	} `json:"choices"`
	Usage map[string]any `json:"usage,omitempty"`
	Error any `json:"error,omitempty"`
}

type aiRateEntry struct {
	Window time.Time
	Count  int
}

var aiRateMu sync.Mutex
var aiRate = map[int64]aiRateEntry{}

func aiModels() []string {
	raw := strings.TrimSpace(os.Getenv("HF_AI_MODELS"))
	if raw == "" {
		return nil
	}
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	seen := map[string]bool{}
	for _, p := range parts {
		m := strings.TrimSpace(p)
		if m != "" && !seen[m] {
			seen[m] = true
			out = append(out, m)
		}
	}
	return out
}

func modelBaseID(model string) string {
	if i := strings.LastIndex(model, ":"); i > strings.LastIndex(model, "/") {
		return model[:i]
	}
	return model
}

func (a *App) registerAIRoutes(r chi.Router) {
	r.Get("/ai/models", a.aiModelsHandler)
	r.Post("/ai/chat", a.aiChatHandler)
	r.Post("/ai/tools/{tool}", a.aiToolHandler)
}

func (a *App) aiModelsHandler(w http.ResponseWriter, r *http.Request) {
	token := strings.TrimSpace(os.Getenv("HF_TOKEN"))
	if token == "" {
		fail(w, http.StatusServiceUnavailable, "AI is not configured: HF_TOKEN is missing")
		return
	}
	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, "https://router.huggingface.co/v1/models", nil)
	if err != nil {
		fail(w, http.StatusInternalServerError, "could not create model request")
		return
	}
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := hfHTTPClient.Do(req)
	if err != nil {
		fail(w, http.StatusBadGateway, "Hugging Face model catalogue is temporarily unavailable")
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		fail(w, http.StatusBadGateway, "Hugging Face model catalogue request failed")
		return
	}
	var payload struct {
		Data []map[string]any `json:"data"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 4<<20)).Decode(&payload); err != nil {
		fail(w, http.StatusBadGateway, "invalid model catalogue response")
		return
	}
	allowed := aiModels()
	if len(allowed) > 0 {
		set := make(map[string]bool, len(allowed))
		for _, model := range allowed { set[model] = true }
		filtered := make([]map[string]any, 0, len(payload.Data))
		for _, model := range payload.Data {
			if id, ok := model["id"].(string); ok && (set[id] || set[modelBaseID(id)]) { filtered = append(filtered, model) }
		}
		payload.Data = filtered
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": payload.Data, "configured_models": allowed, "provider": "huggingface"})
}

func allowAIRequest(userID int64) bool {
	now := time.Now()
	aiRateMu.Lock()
	defer aiRateMu.Unlock()
	entry := aiRate[userID]
	if now.Sub(entry.Window) >= time.Minute || entry.Window.IsZero() {
		entry = aiRateEntry{Window: now}
	}
	if entry.Count >= 10 {
		aiRate[userID] = entry
		return false
	}
	entry.Count++
	aiRate[userID] = entry
	return true
}

func (a *App) aiChatHandler(w http.ResponseWriter, r *http.Request) {
	var in aiChatInput
	if decode(r, &in) != nil {
		fail(w, http.StatusBadRequest, "invalid AI request")
		return
	}
	a.runAI(w, r, in, "")
}

func (a *App) aiToolHandler(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Text       string `json:"text"`
		Context    string `json:"context,omitempty"`
		TargetLang string `json:"target_language,omitempty"`
	}
	if decode(r, &in) != nil || strings.TrimSpace(in.Text) == "" {
		fail(w, http.StatusBadRequest, "text is required")
		return
	}
	if len(in.Text) > 16000 || len(in.Context) > 12000 {
		fail(w, http.StatusRequestEntityTooLarge, "AI input is too long")
		return
	}
	prompts := map[string]string{
		"translate": "Translate the supplied text into the requested target language. Preserve meaning, tone and formatting. Return only the translation.",
		"summarize": "Summarize the supplied text accurately. Preserve important facts and distinguish facts from uncertainty. Use concise bullet points.",
		"rewrite": "Rewrite the supplied text clearly and naturally while preserving its meaning. Return only the rewritten text.",
		"grammar": "Correct spelling, grammar and punctuation without changing the author's meaning or voice. Return only the corrected text.",
		"post": "Create a polished social-media post based on the supplied idea. Do not invent personal facts. Return the post only.",
		"smart_reply": "Suggest three short, context-appropriate reply options. Do not send anything. Label the options 1, 2 and 3.",
		"context_search": "Find and rank the passages in the supplied context that best answer the user's query. Quote only short relevant fragments and explain relevance. If no passage answers it, say so.",
	}
	tool := chi.URLParam(r, "tool")
	system, ok := prompts[tool]
	if !ok {
		fail(w, http.StatusNotFound, "unknown AI tool")
		return
	}
	if tool == "translate" && strings.TrimSpace(in.TargetLang) != "" {
		system += " Target language: " + strings.TrimSpace(in.TargetLang) + "."
	}
	userText := in.Text
	if in.Context != "" {
		userText = "Context:\n" + in.Context + "\n\nTask/input:\n" + in.Text
	}
	a.runAI(w, r, aiChatInput{Messages: []aiMessage{{Role: "user", Content: userText}}}, system)
}

func (a *App) runAI(w http.ResponseWriter, r *http.Request, in aiChatInput, taskPrompt string) {
	if strings.TrimSpace(os.Getenv("HF_TOKEN")) == "" {
		fail(w, http.StatusServiceUnavailable, "AI is not configured: set HF_TOKEN on the server")
		return
	}
	if !allowAIRequest(uid(r)) {
		w.Header().Set("Retry-After", "60")
		fail(w, http.StatusTooManyRequests, "application AI rate limit reached; retry after one minute")
		return
	}
	if len(in.Messages) == 0 || len(in.Messages) > 24 {
		fail(w, http.StatusBadRequest, "messages must contain between 1 and 24 entries")
		return
	}
	total := 0
	for i := range in.Messages {
		m := &in.Messages[i]
		m.Role = strings.TrimSpace(m.Role)
		if m.Role != "user" && m.Role != "assistant" {
			fail(w, http.StatusBadRequest, "message roles must be user or assistant")
			return
		}
		m.Content = strings.TrimSpace(m.Content)
		total += len(m.Content)
		if m.Content == "" || len(m.Content) > 16000 {
			fail(w, http.StatusBadRequest, "each message must contain 1-16000 bytes")
			return
		}
	}
	if in.Messages[len(in.Messages)-1].Role != "user" || total > 48000 {
		fail(w, http.StatusBadRequest, "last message must be from user and total input must not exceed 48000 bytes")
		return
	}
	models := aiModels()
	if len(models) == 0 {
		fail(w, http.StatusServiceUnavailable, "AI is not configured: set HF_AI_MODELS on the server")
		return
	}
	if in.Model != "" {
		allowed := false
		for _, m := range models { if in.Model == m { allowed = true; break } }
		if !allowed {
			fail(w, http.StatusBadRequest, "requested model is not in the server allowlist")
			return
		}
		models = []string{in.Model}
	}
	maxTokens := in.MaxTokens
	if maxTokens <= 0 || maxTokens > 2048 { maxTokens = 1024 }
	temperature := in.Temperature
	if temperature < 0 || temperature > 1.5 { temperature = 0.7 }
	messages := make([]aiMessage, 0, len(in.Messages)+1)
	system := "You are the built-in assistant for vibecodeGram. Be accurate, concise and safe. Do not claim to have performed actions outside this request."
	if taskPrompt != "" { system = taskPrompt + " Follow the user's request precisely." }
	messages = append(messages, aiMessage{Role: "system", Content: system})
	messages = append(messages, in.Messages...)
	token := strings.TrimSpace(os.Getenv("HF_TOKEN"))
	var lastStatus int
	var lastErr string
	for index, model := range models {
		body, err := json.Marshal(map[string]any{
			"model": model, "messages": messages,
			"temperature": temperature, "max_tokens": maxTokens,
		})
		if err != nil {
			fail(w, http.StatusInternalServerError, "could not encode AI request")
			return
		}
		req, err := http.NewRequestWithContext(r.Context(), http.MethodPost, hfChatURL, bytes.NewReader(body))
		if err != nil {
			fail(w, http.StatusInternalServerError, "could not create AI request")
			return
		}
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		resp, err := hfHTTPClient.Do(req)
		if err != nil {
			lastStatus, lastErr = http.StatusBadGateway, "Hugging Face request failed"
			if index < len(models)-1 { continue }
			break
		}
		responseBody, readErr := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
		resp.Body.Close()
		if readErr != nil {
			lastStatus, lastErr = http.StatusBadGateway, "could not read Hugging Face response"
			if index < len(models)-1 { continue }
			break
		}
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			var result hfChatResponse
			if err := json.Unmarshal(responseBody, &result); err != nil || len(result.Choices) == 0 {
				fail(w, http.StatusBadGateway, "Hugging Face returned an invalid completion")
				return
			}
			writeJSON(w, http.StatusOK, map[string]any{
				"reply": result.Choices[0].Message.Content,
				"model": model,
				"provider": "huggingface",
				"usage": result.Usage,
			})
			return
		}
		lastStatus = resp.StatusCode
		lastErr = fmt.Sprintf("Hugging Face request failed with status %d", resp.StatusCode)
		// Do not rotate models to evade account/provider quotas or rate limits.
		if resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode == http.StatusPaymentRequired || resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden || resp.StatusCode == http.StatusBadRequest {
			if retry := resp.Header.Get("Retry-After"); retry != "" { w.Header().Set("Retry-After", retry) }
			writeJSON(w, resp.StatusCode, apiError{Error: lastErr})
			return
		}
		// Fail over only for transient provider/model outages, not quota exhaustion.
		if resp.StatusCode != http.StatusBadGateway && resp.StatusCode != http.StatusServiceUnavailable && resp.StatusCode != http.StatusGatewayTimeout {
			writeJSON(w, resp.StatusCode, apiError{Error: lastErr})
			return
		}
	}
	if lastStatus == 0 { lastStatus = http.StatusBadGateway }
	if lastErr == "" { lastErr = "all configured Hugging Face models are temporarily unavailable" }
	fail(w, lastStatus, lastErr)
}

