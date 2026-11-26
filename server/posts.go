package main

import (
	"bytes"
	"database/sql"
	"errors"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
)

func (a *App) registerPostRoutes(r chi.Router) {
	r.Get("/users/search", a.searchUsers)
	r.Get("/posts", a.listPosts)
	r.Post("/posts", a.createPost)
	r.Post("/posts/video", a.createVideoPost)
	r.Get("/posts/{id}/video", a.getPostVideo)
	r.Post("/posts/{id}/like", a.togglePostLike)
	r.Put("/posts/{id}/save", a.savePost)
	r.Delete("/posts/{id}/save", a.unsavePost)
	r.Post("/posts/{id}/hide", a.hidePost)
	r.Delete("/posts/{id}/hide", a.unhidePost)
}

func postID(r *http.Request) (int64, bool) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	return id, err == nil && id > 0
}

func (a *App) listPosts(w http.ResponseWriter, r *http.Request) {
	sortMode := r.URL.Query().Get("sort")
	order := "p.created_at DESC, p.id DESC"
	if sortMode == "popular" {
		order = "likes_count DESC, p.created_at DESC, p.id DESC"
	}
	whereSaved := ""
	whereHidden := " AND NOT EXISTS(SELECT 1 FROM hidden_posts h WHERE h.post_id=p.id AND h.user_id=$1)"
	whereVideo := ""
	if sortMode == "saved" {
		whereSaved = " AND EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=$1)"
	} else if sortMode == "hidden" {
		whereHidden = " AND EXISTS(SELECT 1 FROM hidden_posts h WHERE h.post_id=p.id AND h.user_id=$1)"
	} else if sortMode == "videos" {
		whereVideo = " AND v.post_id IS NOT NULL"
	}
	query := `SELECT p.id,p.author_id,u.username,u.display_name,p.body,p.tag,p.created_at,
		(SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) AS likes_count,
		EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=$1),
		EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=$1),
		v.mime_type,v.byte_size
		FROM posts p JOIN users u ON u.id=p.author_id
		LEFT JOIN post_videos v ON v.post_id=p.id
		WHERE 1=1` + whereHidden + whereSaved + whereVideo + `
		ORDER BY ` + order + ` LIMIT 100`
	rows, err := a.db.QueryContext(r.Context(), query, uid(r))
	if err != nil { fail(w, 500, "could not load feed"); return }
	defer rows.Close()
	out := make([]map[string]any, 0)
	for rows.Next() {
		var id, author, likes int64
		var username, displayName, body, tag string
		var created interface{}
		var liked, saved bool
		var mediaType sql.NullString
		var mediaSize sql.NullInt64
		if err := rows.Scan(&id,&author,&username,&displayName,&body,&tag,&created,&likes,&liked,&saved,&mediaType,&mediaSize); err != nil {
			fail(w, 500, "could not read feed"); return
		}
		videoURL := ""
		if mediaType.Valid {
			videoURL = "/v1/posts/" + strconv.FormatInt(id, 10) + "/video"
		}
		out = append(out, map[string]any{
			"id":id,"author_id":author,"username":username,"display_name":displayName,
			"body":body,"tag":tag,"created_at":created,"likes":likes,"liked":liked,"saved":saved,
			"media_type":mediaType.String,"media_size":mediaSize.Int64,"video_url":videoURL,
		})
	}
	if err := rows.Err(); err != nil { fail(w, 500, "could not read feed"); return }
	writeJSON(w, 200, out)
}

func (a *App) createPost(w http.ResponseWriter, r *http.Request) {
	var in struct { Body string `json:"body"`; Tag string `json:"tag"` }
	if decode(r,&in) != nil { fail(w,400,"invalid post"); return }
	in.Body = strings.TrimSpace(in.Body)
	in.Tag = strings.TrimSpace(strings.TrimPrefix(in.Tag,"#"))
	if len(in.Body)==0 || len(in.Body)>5000 || len(in.Tag)>32 { fail(w,400,"post text must be 1-5000 characters and tag at most 32"); return }
	var id int64
	var created interface{}
	err := a.db.QueryRowContext(r.Context(),"INSERT INTO posts(author_id,body,tag) VALUES($1,$2,$3) RETURNING id,created_at",uid(r),in.Body,in.Tag).Scan(&id,&created)
	if err != nil { fail(w,500,"could not publish post"); return }
	writeJSON(w,201,map[string]any{"id":id,"author_id":uid(r),"body":in.Body,"tag":in.Tag,"created_at":created,"likes":0,"liked":false,"saved":false})
}

const maxPostVideoBytes int64 = 50 << 20

func (a *App) createVideoPost(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxPostVideoBytes+(2<<20))
	reader, err := r.MultipartReader()
	if err != nil {
		fail(w, 400, "multipart video upload is required")
		return
	}
	body := ""
	tag := ""
	var data []byte
	for {
		part, nextErr := reader.NextPart()
		if errors.Is(nextErr, io.EOF) {
			break
		}
		if nextErr != nil {
			fail(w, 400, "invalid video upload")
			return
		}
		switch part.FormName() {
		case "body":
			value, readErr := io.ReadAll(io.LimitReader(part, 5001))
			if readErr != nil || len(value) > 5000 {
				_ = part.Close()
				fail(w, 400, "post text must be at most 5000 characters")
				return
			}
			body = strings.TrimSpace(string(value))
		case "tag":
			value, readErr := io.ReadAll(io.LimitReader(part, 33))
			if readErr != nil || len(value) > 32 {
				_ = part.Close()
				fail(w, 400, "tag must be at most 32 characters")
				return
			}
			tag = strings.TrimSpace(strings.TrimPrefix(string(value), "#"))
		case "video":
			if data != nil {
				_ = part.Close()
				fail(w, 400, "only one video file is allowed")
				return
			}
			data, err = io.ReadAll(io.LimitReader(part, maxPostVideoBytes+1))
			if err != nil {
				_ = part.Close()
				fail(w, 400, "could not read video")
				return
			}
		}
		_ = part.Close()
	}
	if len(data) == 0 {
		fail(w, 400, "video file is required")
		return
	}
	if len(data) < 12 || int64(len(data)) > maxPostVideoBytes {
		fail(w, 413, "video must be a valid MP4 file up to 50 MB")
		return
	}
	// MP4 containers carry the ftyp box at bytes 4..8.
	if string(data[4:8]) != "ftyp" {
		fail(w, 415, "only MP4 video files are supported")
		return
	}
	if body == "" {
		body = "Видео"
	}
	tx, err := a.db.BeginTx(r.Context(), nil)
	if err != nil {
		fail(w, 500, "could not publish video")
		return
	}
	defer tx.Rollback()
	var id int64
	var created interface{}
	if err = tx.QueryRowContext(r.Context(),
		"INSERT INTO posts(author_id,body,tag) VALUES($1,$2,$3) RETURNING id,created_at",
		uid(r), body, tag).Scan(&id, &created); err != nil {
		fail(w, 500, "could not create video post")
		return
	}
	if _, err = tx.ExecContext(r.Context(),
		"INSERT INTO post_videos(post_id,mime_type,byte_size,data) VALUES($1,'video/mp4',$2,$3)",
		id, len(data), data); err != nil {
		fail(w, 500, "could not save video")
		return
	}
	if err = tx.Commit(); err != nil {
		fail(w, 500, "could not publish video")
		return
	}
	writeJSON(w, 201, map[string]any{
		"id":id,"author_id":uid(r),"body":body,"tag":tag,"created_at":created,
		"likes":0,"liked":false,"saved":false,"media_type":"video/mp4",
		"media_size":len(data),"video_url":"/v1/posts/"+strconv.FormatInt(id,10)+"/video",
	})
}

func (a *App) getPostVideo(w http.ResponseWriter, r *http.Request) {
	id, ok := postID(r)
	if !ok {
		fail(w, 400, "invalid post id")
		return
	}
	var mediaType string
	var data []byte
	err := a.db.QueryRowContext(r.Context(),
		"SELECT v.mime_type,v.data FROM post_videos v JOIN posts p ON p.id=v.post_id WHERE p.id=$1", id,
	).Scan(&mediaType, &data)
	if errors.Is(err, sql.ErrNoRows) {
		fail(w, 404, "video not found")
		return
	}
	if err != nil {
		fail(w, 500, "could not load video")
		return
	}
	w.Header().Set("Content-Type", mediaType)
	w.Header().Set("Cache-Control", "private, max-age=3600")
	http.ServeContent(w, r, "post.mp4", time.Time{}, bytes.NewReader(data))
}

func (a *App) togglePostLike(w http.ResponseWriter, r *http.Request) {
	id,ok:=postID(r); if !ok { fail(w,400,"invalid post id"); return }
	var exists bool
	if err:=a.db.QueryRowContext(r.Context(),"SELECT EXISTS(SELECT 1 FROM posts WHERE id=$1)",id).Scan(&exists); err!=nil || !exists { fail(w,404,"post not found"); return }
	var liked bool
	err:=a.db.QueryRowContext(r.Context(),`WITH removed AS (DELETE FROM post_likes WHERE post_id=$1 AND user_id=$2 RETURNING 1),
		added AS (INSERT INTO post_likes(post_id,user_id) SELECT $1,$2 WHERE NOT EXISTS(SELECT 1 FROM removed) ON CONFLICT DO NOTHING RETURNING 1)
		SELECT EXISTS(SELECT 1 FROM added)`,id,uid(r)).Scan(&liked)
	if err!=nil { fail(w,500,"could not update like"); return }
	if liked {
		var authorID int64
		if authorErr:=a.db.QueryRowContext(r.Context(),"SELECT author_id FROM posts WHERE id=$1",id).Scan(&authorID); authorErr==nil && authorID!=uid(r) {
			_,_=a.db.ExecContext(r.Context(),"INSERT INTO notifications(recipient_id,actor_id,kind,post_id) VALUES($1,$2,'like',$3)",authorID,uid(r),id)
		}
	}
	var count int64
	if err=a.db.QueryRowContext(r.Context(),"SELECT COUNT(*) FROM post_likes WHERE post_id=$1",id).Scan(&count); err!=nil { fail(w,500,"could not update like"); return }
	writeJSON(w,200,map[string]any{"liked":liked,"likes":count})
}

func (a *App) savePost(w http.ResponseWriter,r *http.Request) {
	id,ok:=postID(r); if !ok { fail(w,400,"invalid post id"); return }
	res,err:=a.db.ExecContext(r.Context(),"INSERT INTO saved_posts(post_id,user_id) SELECT id,$2 FROM posts WHERE id=$1 ON CONFLICT DO NOTHING",id,uid(r))
	if err!=nil { fail(w,500,"could not save post"); return }
	n,_:=res.RowsAffected(); if n==0 { var exists bool; _=a.db.QueryRowContext(r.Context(),"SELECT EXISTS(SELECT 1 FROM posts WHERE id=$1)",id).Scan(&exists); if !exists { fail(w,404,"post not found"); return } }
	writeJSON(w,200,map[string]any{"saved":true})
}
func (a *App) unsavePost(w http.ResponseWriter,r *http.Request) {
	id,ok:=postID(r); if !ok { fail(w,400,"invalid post id"); return }
	if _,err:=a.db.ExecContext(r.Context(),"DELETE FROM saved_posts WHERE post_id=$1 AND user_id=$2",id,uid(r)); err!=nil { fail(w,500,"could not remove saved post"); return }
	writeJSON(w,200,map[string]any{"saved":false})
}
func (a *App) hidePost(w http.ResponseWriter,r *http.Request) {
	id,ok:=postID(r); if !ok { fail(w,400,"invalid post id"); return }
	res,err:=a.db.ExecContext(r.Context(),"INSERT INTO hidden_posts(post_id,user_id) SELECT id,$2 FROM posts WHERE id=$1 ON CONFLICT DO NOTHING",id,uid(r))
	if err!=nil { fail(w,500,"could not hide post"); return }
	n,_:=res.RowsAffected(); if n==0 { var exists bool; _=a.db.QueryRowContext(r.Context(),"SELECT EXISTS(SELECT 1 FROM posts WHERE id=$1)",id).Scan(&exists); if !exists { fail(w,404,"post not found"); return } }
	writeJSON(w,200,map[string]any{"hidden":true})
}

func (a *App) unhidePost(w http.ResponseWriter, r *http.Request) {
	id, ok := postID(r)
	if !ok { fail(w, 400, "invalid post id"); return }
	if _, err := a.db.ExecContext(r.Context(), "DELETE FROM hidden_posts WHERE post_id=$1 AND user_id=$2", id, uid(r)); err != nil {
		fail(w, 500, "could not restore post"); return
	}
	writeJSON(w, 200, map[string]any{"hidden": false})
}

func (a *App) searchUsers(w http.ResponseWriter, r *http.Request) {
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if len(q) < 2 || len(q) > 32 { fail(w, 400, "search query must be 2-32 characters"); return }
	rows, err := a.db.QueryContext(r.Context(), `SELECT id,username,display_name FROM users
		WHERE id<>$1 AND (username ILIKE $2 OR display_name ILIKE $2)
		ORDER BY CASE WHEN username ILIKE $3 THEN 0 ELSE 1 END, username LIMIT 30`, uid(r), "%"+q+"%", q)
	if err != nil { fail(w,500,"could not search users"); return }
	defer rows.Close()
	out := make([]map[string]any,0)
	for rows.Next() {
		var id int64; var username, displayName string
		if err:=rows.Scan(&id,&username,&displayName); err!=nil { fail(w,500,"could not read users"); return }
		out=append(out,map[string]any{"id":id,"username":username,"display_name":displayName})
	}
	if err:=rows.Err(); err!=nil { fail(w,500,"could not read users"); return }
	writeJSON(w,200,out)
}
