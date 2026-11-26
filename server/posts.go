package main

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"
)

func (a *App) registerPostRoutes(r chi.Router) {
	r.Get("/users/search", a.searchUsers)
	r.Get("/posts", a.listPosts)
	r.Post("/posts", a.createPost)
	r.Post("/posts/{id}/like", a.togglePostLike)
	r.Put("/posts/{id}/save", a.savePost)
	r.Delete("/posts/{id}/save", a.unsavePost)
	r.Post("/posts/{id}/hide", a.hidePost)
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
	if sortMode == "saved" {
		whereSaved = " AND EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=$1)"
	}
	query := `SELECT p.id,p.author_id,u.username,u.display_name,p.body,p.tag,p.created_at,
		(SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id),
		EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=$1),
		EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=$1)
		FROM posts p JOIN users u ON u.id=p.author_id
		WHERE NOT EXISTS(SELECT 1 FROM hidden_posts h WHERE h.post_id=p.id AND h.user_id=$1)` + whereSaved + `
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
		if err := rows.Scan(&id,&author,&username,&displayName,&body,&tag,&created,&likes,&liked,&saved); err != nil {
			fail(w, 500, "could not read feed"); return
		}
		out = append(out, map[string]any{"id":id,"author_id":author,"username":username,"display_name":displayName,"body":body,"tag":tag,"created_at":created,"likes":likes,"liked":liked,"saved":saved})
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

func (a *App) togglePostLike(w http.ResponseWriter, r *http.Request) {
	id,ok:=postID(r); if !ok { fail(w,400,"invalid post id"); return }
	var exists bool
	if err:=a.db.QueryRowContext(r.Context(),"SELECT EXISTS(SELECT 1 FROM posts WHERE id=$1)",id).Scan(&exists); err!=nil || !exists { fail(w,404,"post not found"); return }
	var liked bool
	err:=a.db.QueryRowContext(r.Context(),`WITH removed AS (DELETE FROM post_likes WHERE post_id=$1 AND user_id=$2 RETURNING 1),
		added AS (INSERT INTO post_likes(post_id,user_id) SELECT $1,$2 WHERE NOT EXISTS(SELECT 1 FROM removed) ON CONFLICT DO NOTHING RETURNING 1)
		SELECT EXISTS(SELECT 1 FROM added)`,id,uid(r)).Scan(&liked)
	if err!=nil { fail(w,500,"could not update like"); return }
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
