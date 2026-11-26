package main

import (
	"database/sql"
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"
)

func (a *App) registerModerationRoutes(r chi.Router) {
	r.Post("/users/{id}/block", a.blockUser)
	r.Delete("/users/{id}/block", a.unblockUser)
	r.Get("/me/blocked", a.listBlockedUsers)
	r.Post("/posts/{id}/report", a.reportPost)
	r.Get("/me/reports", a.listMyReports)
}

func (a *App) blockUser(w http.ResponseWriter, r *http.Request) {
	id, ok := socialID(r)
	if !ok || id == uid(r) {
		fail(w, http.StatusBadRequest, "invalid user id")
		return
	}
	result, err := a.db.ExecContext(r.Context(), `
		INSERT INTO user_blocks(blocker_id, blocked_id)
		SELECT $1, id FROM users WHERE id=$2
		ON CONFLICT (blocker_id, blocked_id) DO NOTHING
	`, uid(r), id)
	if err != nil {
		fail(w, http.StatusInternalServerError, "could not block user")
		return
	}
	n, _ := result.RowsAffected()
	if n == 0 {
		var exists bool
		if err = a.db.QueryRowContext(r.Context(), "SELECT EXISTS(SELECT 1 FROM users WHERE id=$1)", id).Scan(&exists); err != nil {
			fail(w, http.StatusInternalServerError, "could not block user")
			return
		}
		if !exists {
			fail(w, http.StatusNotFound, "user not found")
			return
		}
	}
	_, _ = a.db.ExecContext(r.Context(), "DELETE FROM follows WHERE (follower_id=$1 AND followed_id=$2) OR (follower_id=$2 AND followed_id=$1)", uid(r), id)
	writeJSON(w, http.StatusOK, map[string]any{"blocked": true})
}

func (a *App) unblockUser(w http.ResponseWriter, r *http.Request) {
	id, ok := socialID(r)
	if !ok || id == uid(r) {
		fail(w, http.StatusBadRequest, "invalid user id")
		return
	}
	_, err := a.db.ExecContext(r.Context(), "DELETE FROM user_blocks WHERE blocker_id=$1 AND blocked_id=$2", uid(r), id)
	if err != nil {
		fail(w, http.StatusInternalServerError, "could not unblock user")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"blocked": false})
}

func (a *App) listBlockedUsers(w http.ResponseWriter, r *http.Request) {
	rows, err := a.db.QueryContext(r.Context(), `
		SELECT u.id, u.username, u.display_name, b.created_at
		FROM user_blocks b
		JOIN users u ON u.id=b.blocked_id
		WHERE b.blocker_id=$1
		ORDER BY b.created_at DESC, u.id DESC
		LIMIT 200
	`, uid(r))
	if err != nil {
		fail(w, http.StatusInternalServerError, "could not load blocked users")
		return
	}
	defer rows.Close()
	out := make([]map[string]any, 0)
	for rows.Next() {
		var id int64
		var username, displayName string
		var createdAt any
		if err := rows.Scan(&id, &username, &displayName, &createdAt); err != nil {
			fail(w, http.StatusInternalServerError, "could not read blocked users")
			return
		}
		out = append(out, map[string]any{
			"id": id, "username": username, "display_name": displayName, "blocked_at": createdAt,
		})
	}
	if err := rows.Err(); err != nil {
		fail(w, http.StatusInternalServerError, "could not read blocked users")
		return
	}
	writeJSON(w, http.StatusOK, out)
}

func (a *App) reportPost(w http.ResponseWriter, r *http.Request) {
	id, ok := postID(r)
	if !ok {
		fail(w, http.StatusBadRequest, "invalid post id")
		return
	}
	var in struct {
		Reason  string `json:"reason"`
		Details string `json:"details"`
	}
	if decode(r, &in) != nil {
		fail(w, http.StatusBadRequest, "invalid report")
		return
	}
	in.Reason = strings.TrimSpace(strings.ToLower(in.Reason))
	in.Details = strings.TrimSpace(in.Details)
	switch in.Reason {
	case "spam", "harassment", "violence", "misinformation", "copyright", "other":
	default:
		fail(w, http.StatusBadRequest, "unsupported report reason")
		return
	}
	if len(in.Details) > 1000 {
		fail(w, http.StatusBadRequest, "report details must be at most 1000 characters")
		return
	}
	var reportID int64
	err := a.db.QueryRowContext(r.Context(), `
		INSERT INTO post_reports(reporter_id, post_id, reason, details)
		SELECT $1, id, $3, $4 FROM posts WHERE id=$2
		ON CONFLICT (reporter_id, post_id)
		DO UPDATE SET reason=EXCLUDED.reason, details=EXCLUDED.details, status='pending', updated_at=NOW()
		RETURNING id
	`, uid(r), id, in.Reason, in.Details).Scan(&reportID)
	if errors.Is(err, sql.ErrNoRows) {
		fail(w, http.StatusNotFound, "post not found")
		return
	}
	if err != nil {
		fail(w, http.StatusInternalServerError, "could not submit report")
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"reported": true, "report_id": reportID})
}

func (a *App) listMyReports(w http.ResponseWriter, r *http.Request) {
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit < 1 || limit > 100 {
		limit = 50
	}
	rows, err := a.db.QueryContext(r.Context(), `
		SELECT id, post_id, reason, details, status, created_at, updated_at
		FROM post_reports
		WHERE reporter_id=$1
		ORDER BY id DESC
		LIMIT $2
	`, uid(r), limit)
	if err != nil {
		fail(w, http.StatusInternalServerError, "could not load reports")
		return
	}
	defer rows.Close()
	out := make([]map[string]any, 0)
	for rows.Next() {
		var id, postID int64
		var reason, details, status string
		var createdAt, updatedAt any
		if err := rows.Scan(&id, &postID, &reason, &details, &status, &createdAt, &updatedAt); err != nil {
			fail(w, http.StatusInternalServerError, "could not read reports")
			return
		}
		out = append(out, map[string]any{
			"id": id, "post_id": postID, "reason": reason, "details": details,
			"status": status, "created_at": createdAt, "updated_at": updatedAt,
		})
	}
	if err := rows.Err(); err != nil {
		fail(w, http.StatusInternalServerError, "could not read reports")
		return
	}
	writeJSON(w, http.StatusOK, out)
}
