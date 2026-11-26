package main

import (
    "database/sql"
    "errors"
    "io"
    "net/http"
    "strconv"
    "strings"
    "time"

    "github.com/go-chi/chi/v5"
)

const maxStoryBytes int64 = 15 << 20

func (a *App) registerSocialRoutes(r chi.Router) {
    r.Get("/stories", a.listStories)
    r.Post("/stories", a.createStory)
    r.Get("/stories/{id}/media", a.getStoryMedia)
    r.Post("/stories/{id}/view", a.viewStory)
    r.Delete("/stories/{id}", a.deleteStory)
    r.Get("/users/{id}", a.getUserProfile)
    r.Put("/me/profile", a.updateMyProfile)
    r.Post("/users/{id}/follow", a.followUser)
    r.Delete("/users/{id}/follow", a.unfollowUser)
    r.Get("/users/{id}/followers", a.listFollowers)
    r.Get("/users/{id}/following", a.listFollowing)
    r.Get("/posts/search", a.searchPosts)
    r.Get("/recommendations", a.recommendations)
    r.Get("/posts/{id}/comments", a.listPostComments)
    r.Post("/posts/{id}/comments", a.createPostComment)
    r.Get("/notifications", a.listNotifications)
    r.Post("/notifications/read", a.markNotificationsRead)
}

func socialID(r *http.Request) (int64, bool) {
    id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
    return id, err == nil && id > 0
}

func (a *App) listStories(w http.ResponseWriter, r *http.Request) {
    rows, err := a.db.QueryContext(r.Context(), `
        SELECT s.id,s.author_id,u.username,u.display_name,s.caption,s.media_type,s.media_size,
               s.created_at,s.expires_at,
               EXISTS(SELECT 1 FROM story_views sv WHERE sv.story_id=s.id AND sv.viewer_id=$1)
        FROM stories s JOIN users u ON u.id=s.author_id
        WHERE s.expires_at>NOW()
          AND (s.author_id=$1 OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=$1 AND f.followed_id=s.author_id))
          AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=s.author_id) OR (b.blocker_id=s.author_id AND b.blocked_id=$1))
        ORDER BY s.created_at DESC LIMIT 200`, uid(r))
    if err != nil { fail(w,500,"could not load stories"); return }
    defer rows.Close()
    out:=make([]map[string]any,0)
    for rows.Next() {
        var id,author,size int64
        var username,name,caption,mime string
        var created,expires time.Time
        var viewed bool
        if err=rows.Scan(&id,&author,&username,&name,&caption,&mime,&size,&created,&expires,&viewed); err!=nil { fail(w,500,"could not read stories"); return }
        out=append(out,map[string]any{"id":id,"author_id":author,"username":username,"display_name":name,"caption":caption,"media_type":mime,"media_size":size,"created_at":created,"expires_at":expires,"viewed":viewed,"media_url":"/v1/stories/"+strconv.FormatInt(id,10)+"/media"})
    }
    if rows.Err()!=nil { fail(w,500,"could not read stories"); return }
    writeJSON(w,200,out)
}

func (a *App) createStory(w http.ResponseWriter,r *http.Request) {
    r.Body=http.MaxBytesReader(w,r.Body,maxStoryBytes+(1<<20))
    reader,err:=r.MultipartReader()
    if err!=nil { fail(w,400,"multipart story upload is required"); return }
    caption,duration:="","24"
    var data []byte
    mime:=""
    for {
        part,nextErr:=reader.NextPart()
        if errors.Is(nextErr,io.EOF) { break }
        if nextErr!=nil { fail(w,400,"invalid story upload"); return }
        switch part.FormName() {
        case "caption":
            b,e:=io.ReadAll(io.LimitReader(part,501)); if e!=nil||len(b)>500 { _=part.Close(); fail(w,400,"caption must be at most 500 characters"); return }; caption=strings.TrimSpace(string(b))
        case "duration_hours":
            b,e:=io.ReadAll(io.LimitReader(part,8)); if e!=nil { _=part.Close(); fail(w,400,"invalid duration"); return }; duration=strings.TrimSpace(string(b))
        case "media":
            if data!=nil { _=part.Close(); fail(w,400,"only one media file is allowed"); return }
            mime=strings.ToLower(strings.TrimSpace(strings.Split(part.Header.Get("Content-Type"),";")[0]))
            data,err=io.ReadAll(io.LimitReader(part,maxStoryBytes+1)); if err!=nil { _=part.Close(); fail(w,400,"could not read story media"); return }
        }
        _=part.Close()
    }
    hours,err:=strconv.Atoi(duration)
    if err!=nil || (hours!=5&&hours!=12&&hours!=24&&hours!=48) { fail(w,400,"duration_hours must be 5, 12, 24 or 48"); return }
    if len(data)==0 || int64(len(data))>maxStoryBytes { fail(w,413,"story media is required and must be at most 15 MB"); return }
    if mime!="image/jpeg"&&mime!="image/png"&&mime!="image/webp"&&mime!="video/mp4" { fail(w,415,"supported story media: JPEG, PNG, WebP or MP4"); return }
    expires:=time.Now().UTC().Add(time.Duration(hours)*time.Hour)
    var id int64
    var created time.Time
    err=a.db.QueryRowContext(r.Context(),`INSERT INTO stories(author_id,caption,media_type,media_size,data,expires_at)
        VALUES($1,$2,$3,$4,$5,$6) RETURNING id,created_at`,uid(r),caption,mime,len(data),data,expires).Scan(&id,&created)
    if err!=nil { fail(w,500,"could not publish story"); return }
    writeJSON(w,201,map[string]any{"id":id,"author_id":uid(r),"caption":caption,"media_type":mime,"media_size":len(data),"created_at":created,"expires_at":expires,"media_url":"/v1/stories/"+strconv.FormatInt(id,10)+"/media"})
}

func (a *App) getStoryMedia(w http.ResponseWriter,r *http.Request) {
    id,ok:=socialID(r); if !ok { fail(w,400,"invalid story id"); return }
    var mime string
    var data []byte
    err:=a.db.QueryRowContext(r.Context(),`SELECT s.media_type,s.data FROM stories s
        WHERE s.id=$1 AND s.expires_at>NOW()
        AND (s.author_id=$2 OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=$2 AND f.followed_id=s.author_id))
        AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=s.author_id) OR (b.blocker_id=s.author_id AND b.blocked_id=$2))`,id,uid(r)).Scan(&mime,&data)
    if errors.Is(err,sql.ErrNoRows) { fail(w,404,"story not found"); return }
    if err!=nil { fail(w,500,"could not load story"); return }
    w.Header().Set("Content-Type",mime)
    w.Header().Set("Cache-Control","private, max-age=60")
    w.WriteHeader(http.StatusOK)
    _,_=w.Write(data)
}

func (a *App) viewStory(w http.ResponseWriter,r *http.Request) {
    id,ok:=socialID(r); if !ok { fail(w,400,"invalid story id"); return }
    result,err:=a.db.ExecContext(r.Context(),`INSERT INTO story_views(story_id,viewer_id)
        SELECT id,$2 FROM stories WHERE id=$1 AND expires_at>NOW()
        AND (author_id=$2 OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=$2 AND f.followed_id=author_id))
        AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=author_id) OR (b.blocker_id=author_id AND b.blocked_id=$2))
        ON CONFLICT DO NOTHING`,id,uid(r))
    if err!=nil { fail(w,500,"could not record story view"); return }
    n,_:=result.RowsAffected()
    if n==0 {
        var accessible bool
        checkErr := a.db.QueryRowContext(r.Context(), `
            SELECT EXISTS(
                SELECT 1 FROM stories s
                WHERE s.id=$1 AND s.expires_at>NOW()
                  AND (s.author_id=$2 OR EXISTS(
                    SELECT 1 FROM follows f WHERE f.follower_id=$2 AND f.followed_id=s.author_id
                  ))
                  AND NOT EXISTS(
                    SELECT 1 FROM user_blocks b
                    WHERE (b.blocker_id=$2 AND b.blocked_id=s.author_id)
                       OR (b.blocker_id=s.author_id AND b.blocked_id=$2)
                  )
            )
        `, id, uid(r)).Scan(&accessible)
        if checkErr != nil || !accessible { fail(w,404,"story not found"); return }
    }
    writeJSON(w,200,map[string]any{"viewed":true})
}

func (a *App) deleteStory(w http.ResponseWriter,r *http.Request) {
    id,ok:=socialID(r); if !ok { fail(w,400,"invalid story id"); return }
    res,err:=a.db.ExecContext(r.Context(),"DELETE FROM stories WHERE id=$1 AND author_id=$2",id,uid(r))
    if err!=nil { fail(w,500,"could not delete story"); return }
    n,_:=res.RowsAffected()
    writeJSON(w,200,map[string]any{"deleted":n>0})
}

func (a *App) getUserProfile(w http.ResponseWriter,r *http.Request) {
    id,ok:=socialID(r); if !ok { fail(w,400,"invalid user id"); return }
    var username,name,bio,avatar,cover,website,location string
    var followers,following,posts int64
    var followed bool
    err:=a.db.QueryRowContext(r.Context(),`SELECT u.username,u.display_name,COALESCE(p.bio,''),COALESCE(p.avatar_url,''),COALESCE(p.cover_url,''),COALESCE(p.website,''),COALESCE(p.location,''),
        (SELECT COUNT(*) FROM follows f WHERE f.followed_id=u.id),
        (SELECT COUNT(*) FROM follows f WHERE f.follower_id=u.id),
        (SELECT COUNT(*) FROM posts po WHERE po.author_id=u.id),
        EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=$2 AND f.followed_id=u.id)
        FROM users u LEFT JOIN user_profiles p ON p.user_id=u.id WHERE u.id=$1 AND ($1=$2 OR NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=u.id) OR (b.blocker_id=u.id AND b.blocked_id=$2)))`,id,uid(r)).Scan(&username,&name,&bio,&avatar,&cover,&website,&location,&followers,&following,&posts,&followed)
    if errors.Is(err,sql.ErrNoRows) { fail(w,404,"user not found"); return }
    if err!=nil { fail(w,500,"could not load profile"); return }
    writeJSON(w,200,map[string]any{"id":id,"username":username,"display_name":name,"bio":bio,"avatar_url":avatar,"cover_url":cover,"website":website,"location":location,"followers":followers,"following":following,"posts":posts,"followed":followed,"is_me":id==uid(r)})
}

func (a *App) updateMyProfile(w http.ResponseWriter,r *http.Request) {
    var in struct { DisplayName string `json:"display_name"`; Bio string `json:"bio"`; AvatarURL string `json:"avatar_url"`; CoverURL string `json:"cover_url"`; Website string `json:"website"`; Location string `json:"location"` }
    if decode(r,&in)!=nil { fail(w,400,"invalid profile"); return }
    in.DisplayName=strings.TrimSpace(in.DisplayName); in.Bio=strings.TrimSpace(in.Bio); in.AvatarURL=strings.TrimSpace(in.AvatarURL); in.CoverURL=strings.TrimSpace(in.CoverURL); in.Website=strings.TrimSpace(in.Website); in.Location=strings.TrimSpace(in.Location)
    if len(in.DisplayName)<1||len(in.DisplayName)>64||len(in.Bio)>160||len(in.AvatarURL)>2048||len(in.CoverURL)>2048||len(in.Website)>255||len(in.Location)>100 { fail(w,400,"profile fields exceed allowed lengths"); return }
    tx,err:=a.db.BeginTx(r.Context(),nil); if err!=nil { fail(w,500,"database error"); return }; defer tx.Rollback()
    if _,err=tx.ExecContext(r.Context(),"UPDATE users SET display_name=$1 WHERE id=$2",in.DisplayName,uid(r)); err!=nil { fail(w,500,"could not update display name"); return }
    if _,err=tx.ExecContext(r.Context(),`INSERT INTO user_profiles(user_id,bio,avatar_url,cover_url,website,location)
        VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT(user_id) DO UPDATE SET bio=EXCLUDED.bio,avatar_url=EXCLUDED.avatar_url,cover_url=EXCLUDED.cover_url,website=EXCLUDED.website,location=EXCLUDED.location,updated_at=NOW()`,uid(r),in.Bio,in.AvatarURL,in.CoverURL,in.Website,in.Location); err!=nil { fail(w,500,"could not update profile"); return }
    if err=tx.Commit(); err!=nil { fail(w,500,"could not save profile"); return }
    writeJSON(w,200,map[string]any{"updated":true})
}

func (a *App) followUser(w http.ResponseWriter,r *http.Request) {
    id,ok:=socialID(r); if !ok||id==uid(r) { fail(w,400,"invalid user id"); return }
    res,err:=a.db.ExecContext(r.Context(),`INSERT INTO follows(follower_id,followed_id)
        SELECT $1,id FROM users WHERE id=$2 AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=id) OR (b.blocker_id=id AND b.blocked_id=$1)) ON CONFLICT DO NOTHING`,uid(r),id)
    if err!=nil { fail(w,500,"could not follow user"); return }
    n,_:=res.RowsAffected()
    if n==0 {
        var exists bool
        if checkErr := a.db.QueryRowContext(r.Context(), "SELECT EXISTS(SELECT 1 FROM users WHERE id=$1)", id).Scan(&exists); checkErr != nil {
            fail(w,500,"could not verify user"); return
        }
        if !exists { fail(w,404,"user not found"); return }
        var blocked bool
        if checkErr := a.db.QueryRowContext(r.Context(), "SELECT EXISTS(SELECT 1 FROM user_blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1))", uid(r), id).Scan(&blocked); checkErr != nil {
            fail(w,500,"could not verify block status"); return
        }
        if blocked { fail(w,403,"user interaction is blocked"); return }
    }
    if n>0 { _,_=a.db.ExecContext(r.Context(),"INSERT INTO notifications(recipient_id,actor_id,kind) VALUES($1,$2,'follow')",id,uid(r)) }
    writeJSON(w,200,map[string]any{"following":true})
}
func (a *App) unfollowUser(w http.ResponseWriter,r *http.Request) {
    id,ok:=socialID(r); if !ok { fail(w,400,"invalid user id"); return }
    _,err:=a.db.ExecContext(r.Context(),"DELETE FROM follows WHERE follower_id=$1 AND followed_id=$2",uid(r),id)
    if err!=nil { fail(w,500,"could not unfollow user"); return }
    writeJSON(w,200,map[string]any{"following":false})
}

func (a *App) listPeople(w http.ResponseWriter,r *http.Request,following bool) {
    id,ok:=socialID(r); if !ok { fail(w,400,"invalid user id"); return }
    query:=`SELECT u.id,u.username,u.display_name FROM follows f JOIN users u ON u.id=f.followed_id WHERE f.follower_id=$1 ORDER BY f.created_at DESC LIMIT 100`
    if !following { query=`SELECT u.id,u.username,u.display_name FROM follows f JOIN users u ON u.id=f.follower_id WHERE f.followed_id=$1 ORDER BY f.created_at DESC LIMIT 100` }
    rows,err:=a.db.QueryContext(r.Context(),query,id); if err!=nil { fail(w,500,"could not load users"); return }; defer rows.Close()
    out:=make([]map[string]any,0)
    for rows.Next(){ var userID int64; var username,name string; if err=rows.Scan(&userID,&username,&name); err!=nil { fail(w,500,"could not read users"); return }; out=append(out,map[string]any{"id":userID,"username":username,"display_name":name}) }
    if rows.Err()!=nil { fail(w,500,"could not read users"); return }; writeJSON(w,200,out)
}
func (a *App) listFollowers(w http.ResponseWriter,r *http.Request){ a.listPeople(w,r,false) }
func (a *App) listFollowing(w http.ResponseWriter,r *http.Request){ a.listPeople(w,r,true) }

func (a *App) searchPosts(w http.ResponseWriter,r *http.Request) {
    q:=strings.TrimSpace(r.URL.Query().Get("q")); if len(q)<2||len(q)>80 { fail(w,400,"search query must be 2-80 characters"); return }
    pattern:="%"+q+"%"
    rows,err:=a.db.QueryContext(r.Context(),`SELECT p.id,p.author_id,u.username,u.display_name,p.body,p.tag,p.created_at,
        (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id),
        EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=$1),
        EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=$1),
        COALESCE(v.mime_type,''),COALESCE(v.byte_size,0)
        FROM posts p JOIN users u ON u.id=p.author_id LEFT JOIN post_videos v ON v.post_id=p.id
        WHERE p.content_kind='post' AND (p.body ILIKE $2 OR p.tag ILIKE $2 OR u.username ILIKE $2 OR u.display_name ILIKE $2)
        AND NOT EXISTS(SELECT 1 FROM hidden_posts h WHERE h.post_id=p.id AND h.user_id=$1)
        AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=p.author_id) OR (b.blocker_id=p.author_id AND b.blocked_id=$1))
        ORDER BY p.created_at DESC LIMIT 50`,uid(r),pattern)
    if err!=nil { fail(w,500,"could not search posts"); return }; defer rows.Close()
    out:=make([]map[string]any,0)
    for rows.Next(){ var id,author,likes,size int64; var username,name,body,tag,mime string; var created time.Time; var liked,saved bool
        if err=rows.Scan(&id,&author,&username,&name,&body,&tag,&created,&likes,&liked,&saved,&mime,&size); err!=nil { fail(w,500,"could not read search results"); return }
        videoURL:=""; if mime!="" { videoURL="/v1/posts/"+strconv.FormatInt(id,10)+"/video" }
        out=append(out,map[string]any{"id":id,"author_id":author,"username":username,"display_name":name,"body":body,"tag":tag,"created_at":created,"likes":likes,"liked":liked,"saved":saved,"media_type":mime,"media_size":size,"video_url":videoURL})
    }
    if rows.Err()!=nil { fail(w,500,"could not read search results"); return }; writeJSON(w,200,out)
}

func (a *App) recommendations(w http.ResponseWriter,r *http.Request) {
    rows,err:=a.db.QueryContext(r.Context(),`SELECT p.id,p.author_id,u.username,u.display_name,p.body,p.tag,p.created_at,
        (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.id) AS likes,
        EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id=p.id AND l.user_id=$1),
        EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=$1),
        COALESCE(v.mime_type,''),COALESCE(v.byte_size,0)
        FROM posts p JOIN users u ON u.id=p.author_id LEFT JOIN post_videos v ON v.post_id=p.id
        WHERE p.content_kind='post' AND p.author_id<>$1 AND NOT EXISTS(SELECT 1 FROM hidden_posts h WHERE h.post_id=p.id AND h.user_id=$1)
        AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=p.author_id) OR (b.blocker_id=p.author_id AND b.blocked_id=$1))
        ORDER BY
          CASE WHEN EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=$1 AND f.followed_id=p.author_id) THEN 1 ELSE 0 END,
          likes DESC,p.created_at DESC LIMIT 50`,uid(r))
    if err!=nil { fail(w,500,"could not load recommendations"); return }; defer rows.Close()
    out:=make([]map[string]any,0)
    for rows.Next(){ var id,author,likes,size int64; var username,name,body,tag,mime string; var created time.Time; var liked,saved bool
        if err=rows.Scan(&id,&author,&username,&name,&body,&tag,&created,&likes,&liked,&saved,&mime,&size); err!=nil { fail(w,500,"could not read recommendations"); return }
        videoURL:=""; if mime!="" { videoURL="/v1/posts/"+strconv.FormatInt(id,10)+"/video" }
        out=append(out,map[string]any{"id":id,"author_id":author,"username":username,"display_name":name,"body":body,"tag":tag,"created_at":created,"likes":likes,"liked":liked,"saved":saved,"media_type":mime,"media_size":size,"video_url":videoURL})
    }
    if rows.Err()!=nil { fail(w,500,"could not read recommendations"); return }; writeJSON(w,200,out)
}

func (a *App) listPostComments(w http.ResponseWriter,r *http.Request) {
    id,ok:=postID(r); if !ok { fail(w,400,"invalid post id"); return }
    rows,err:=a.db.QueryContext(r.Context(),`SELECT c.id,c.author_id,u.username,u.display_name,c.body,c.created_at
        FROM post_comments c JOIN users u ON u.id=c.author_id WHERE c.post_id=$1 ORDER BY c.id DESC LIMIT 100`,id)
    if err!=nil { fail(w,500,"could not load comments"); return }; defer rows.Close()
    out:=make([]map[string]any,0)
    for rows.Next(){ var cid,author int64; var username,name,body string; var created time.Time; if err=rows.Scan(&cid,&author,&username,&name,&body,&created); err!=nil { fail(w,500,"could not read comments"); return }; out=append(out,map[string]any{"id":cid,"author_id":author,"username":username,"display_name":name,"body":body,"created_at":created}) }
    if rows.Err()!=nil { fail(w,500,"could not read comments"); return }; writeJSON(w,200,out)
}
func (a *App) createPostComment(w http.ResponseWriter,r *http.Request) {
    id,ok:=postID(r); if !ok { fail(w,400,"invalid post id"); return }
    var in struct{ Body string `json:"body"` }; if decode(r,&in)!=nil { fail(w,400,"invalid comment"); return }
    in.Body=strings.TrimSpace(in.Body); if len(in.Body)==0||len(in.Body)>2000 { fail(w,400,"comment must be 1-2000 characters"); return }
    var author int64
    err:=a.db.QueryRowContext(r.Context(),`INSERT INTO post_comments(post_id,author_id,body)
        SELECT id,$2,$3 FROM posts WHERE id=$1 RETURNING author_id`,id,uid(r),in.Body).Scan(&author)
    if errors.Is(err,sql.ErrNoRows) { fail(w,404,"post not found"); return }
    if err!=nil { fail(w,500,"could not publish comment"); return }
    if author!=uid(r) { _,_=a.db.ExecContext(r.Context(),"INSERT INTO notifications(recipient_id,actor_id,kind,post_id) VALUES($1,$2,'comment',$3)",author,uid(r),id) }
    writeJSON(w,201,map[string]any{"created":true})
}

func (a *App) listNotifications(w http.ResponseWriter,r *http.Request) {
    rows,err:=a.db.QueryContext(r.Context(),`SELECT n.id,n.actor_id,u.username,u.display_name,n.kind,n.post_id,n.story_id,n.created_at,n.read_at
        FROM notifications n JOIN users u ON u.id=n.actor_id WHERE n.recipient_id=$1 ORDER BY n.id DESC LIMIT 100`,uid(r))
    if err!=nil { fail(w,500,"could not load notifications"); return }; defer rows.Close()
    out:=make([]map[string]any,0)
    for rows.Next(){ var id,actor int64; var username,name,kind string; var postID,storyID sql.NullInt64; var created time.Time; var readAt sql.NullTime
        if err=rows.Scan(&id,&actor,&username,&name,&kind,&postID,&storyID,&created,&readAt); err!=nil { fail(w,500,"could not read notifications"); return }
        item:=map[string]any{"id":id,"actor_id":actor,"username":username,"display_name":name,"kind":kind,"created_at":created,"read":readAt.Valid}
        if postID.Valid { item["post_id"]=postID.Int64 }; if storyID.Valid { item["story_id"]=storyID.Int64 }; out=append(out,item)
    }
    if rows.Err()!=nil { fail(w,500,"could not read notifications"); return }; writeJSON(w,200,out)
}
func (a *App) markNotificationsRead(w http.ResponseWriter,r *http.Request) {
    _,err:=a.db.ExecContext(r.Context(),"UPDATE notifications SET read_at=NOW() WHERE recipient_id=$1 AND read_at IS NULL",uid(r))
    if err!=nil { fail(w,500,"could not mark notifications read"); return }; writeJSON(w,200,map[string]any{"read":true})
}
