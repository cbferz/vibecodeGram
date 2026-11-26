package main

import (
 "context"
 "crypto/rand"
 "database/sql"
 "encoding/hex"
 "embed"
 "encoding/json"
 "errors"
 "log"
 "net/http"
 "os"
 "strconv"
 "strings"
 "time"

 "github.com/go-chi/chi/v5"
 "github.com/go-chi/chi/v5/middleware"
 "github.com/golang-jwt/jwt/v5"
 "github.com/gorilla/websocket"
 _ "github.com/jackc/pgx/v5/stdlib"
 "golang.org/x/crypto/bcrypt"
)

//go:embed migrations/*.sql
var migrationFS embed.FS

type App struct { db *sql.DB; jwtSecret []byte }
type Claims struct { UserID int64 `json:"uid"`; SessionID string `json:"sid"`; jwt.RegisteredClaims }
type contextKey string
const userKey contextKey = "user_id"

type apiError struct { Error string `json:"error"` }
func writeJSON(w http.ResponseWriter, status int, v any) { w.Header().Set("Content-Type","application/json"); w.WriteHeader(status); _ = json.NewEncoder(w).Encode(v) }
func fail(w http.ResponseWriter, status int, message string) { writeJSON(w,status,apiError{Error:message}) }
func decode(r *http.Request, dst any) error { d:=json.NewDecoder(http.MaxBytesReader(nil,r.Body,1<<20)); d.DisallowUnknownFields(); return d.Decode(dst) }

func main() {
 dsn:=os.Getenv("DATABASE_URL"); secret:=os.Getenv("JWT_SECRET")
 if dsn=="" || len(secret)<32 { log.Fatal("DATABASE_URL and JWT_SECRET (at least 32 bytes) are required") }
 db,err:=sql.Open("pgx",dsn); if err!=nil { log.Fatal(err) }; defer db.Close()
 db.SetMaxOpenConns(25); db.SetMaxIdleConns(5); db.SetConnMaxLifetime(30*time.Minute)
 ctx,cancel:=context.WithTimeout(context.Background(),30*time.Second); defer cancel()
 for { if err=db.PingContext(ctx); err==nil { break }; if ctx.Err()!=nil { log.Fatal("database unavailable: ",err) }; time.Sleep(time.Second) }
 migrationFiles,err:=migrationFS.ReadDir("migrations"); if err!=nil { log.Fatal(err) }
 for _,file:=range migrationFiles { if file.IsDir() || !strings.HasSuffix(file.Name(),".sql") { continue }; migration,readErr:=migrationFS.ReadFile("migrations/"+file.Name()); if readErr!=nil { log.Fatal(readErr) }; if _,err=db.ExecContext(ctx,string(migration)); err!=nil { log.Fatalf("migration %s: %v",file.Name(),err) } }
 a:=&App{db:db,jwtSecret:[]byte(secret)}
 if founderRaw:=strings.TrimSpace(os.Getenv("FOUNDER_USER_ID")); founderRaw!="" { if founderID,parseErr:=strconv.ParseInt(founderRaw,10,64); parseErr==nil && founderID>0 { if _,seedErr:=db.ExecContext(ctx,"INSERT INTO user_badges(user_id,badge_code) SELECT id,'founder' FROM users WHERE id=$1 ON CONFLICT DO NOTHING",founderID); seedErr!=nil { log.Fatal("founder badge bootstrap: ",seedErr) } } }
 r:=chi.NewRouter(); r.Use(middleware.RequestID,middleware.RealIP,middleware.Recoverer,middleware.Timeout(20*time.Second))
 r.Get("/healthz",func(w http.ResponseWriter,r *http.Request){ if err:=db.PingContext(r.Context()); err!=nil { fail(w,503,"database unavailable"); return }; writeJSON(w,200,map[string]string{"status":"ok","service":"vibecodegram-api"}) })
 r.Route("/v1",func(v chi.Router){
  v.Post("/auth/register",a.register); v.Post("/auth/login",a.login)
  v.Group(func(p chi.Router){ p.Use(a.authenticate); a.registerAIRoutes(p); p.Get("/me",a.me); p.Get("/me/badges",a.myBadges); p.Get("/sessions",a.listSessions); p.Delete("/sessions/{sessionID}",a.revokeSession); p.Post("/auth/logout",a.logout); p.Get("/devices/accounts",a.listDeviceAccounts); p.Post("/devices/accounts",a.addDeviceAccount); p.Delete("/devices/accounts/{accountID}",a.removeDeviceAccount); p.Post("/admin/users/{id}/badges",a.grantBadge); p.Delete("/admin/users/{id}/badges/{badge}",a.revokeBadge); a.registerPostRoutes(p); p.Get("/conversations",a.listConversations); p.Post("/conversations",a.createConversation); p.Get("/conversations/{id}/messages",a.listMessages); p.Post("/conversations/{id}/messages",a.sendMessage) })
 })
 r.Put("/internal/subscriptions/{id}",a.subscriptionWebhook)
 r.Get("/v1/ws",a.websocket)
 addr:=os.Getenv("HTTP_ADDR"); if addr=="" { addr=":8080" }
 srv:=&http.Server{Addr:addr,Handler:r,ReadHeaderTimeout:5*time.Second}
 log.Printf("VibeCodeGram API listening on %s",addr)
 log.Fatal(srv.ListenAndServe())
}
func newSessionID() (string,error) { b:=make([]byte,16); if _,err:=rand.Read(b); err!=nil{return "",err}; return hex.EncodeToString(b),nil }
func (a *App) issueToken(id int64)(string,error){
 now:=time.Now(); sid,err:=newSessionID(); if err!=nil{return "",err}; expires:=now.Add(30*24*time.Hour)
 if _,err=a.db.Exec("INSERT INTO sessions(id,user_id,expires_at) VALUES($1,$2,$3)",sid,id,expires); err!=nil{return "",err}
 c:=Claims{UserID:id,SessionID:sid,RegisteredClaims:jwt.RegisteredClaims{ID:sid,Issuer:"vibecodegram",Subject:strconv.FormatInt(id,10),IssuedAt:jwt.NewNumericDate(now),ExpiresAt:jwt.NewNumericDate(expires)}}
 token,err:=jwt.NewWithClaims(jwt.SigningMethodHS256,c).SignedString(a.jwtSecret); if err!=nil{_,_=a.db.Exec("DELETE FROM sessions WHERE id=$1",sid);return "",err}; return token,nil
}
func (a *App) parseClaims(raw string)(*Claims,error){
 t,e:=jwt.ParseWithClaims(raw,&Claims{},func(t *jwt.Token)(any,error){if t.Method!=jwt.SigningMethodHS256{return nil,errors.New("unexpected signing method")};return a.jwtSecret,nil})
 if e!=nil{return nil,e}; c,ok:=t.Claims.(*Claims); if !ok||!t.Valid||c.UserID<1||c.SessionID==""||c.ID!=c.SessionID{return nil,errors.New("invalid token")}; return c,nil
}
func (a *App) parseToken(raw string)(int64,error){c,e:=a.parseClaims(raw);if e!=nil{return 0,e};return c.UserID,nil}
func bearer(r *http.Request)string{ h:=strings.SplitN(r.Header.Get("Authorization")," ",2); if len(h)==2&&strings.EqualFold(h[0],"Bearer"){return h[1]}; return "" }
type sessionContextKey string
const sessionKey sessionContextKey = "session_id"
func (a *App) authenticate(next http.Handler)http.Handler{return http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){
 c,e:=a.parseClaims(bearer(r)); if e!=nil{fail(w,401,"authentication required");return}
 var active bool
 e=a.db.QueryRowContext(r.Context(),"SELECT EXISTS(SELECT 1 FROM sessions WHERE id=$1 AND user_id=$2 AND revoked_at IS NULL AND expires_at>NOW())",c.SessionID,c.UserID).Scan(&active)
 if e!=nil||!active{fail(w,401,"session expired or revoked");return}
 _,_=a.db.ExecContext(r.Context(),"UPDATE sessions SET last_seen_at=NOW() WHERE id=$1",c.SessionID)
 ctx:=context.WithValue(r.Context(),userKey,c.UserID); ctx=context.WithValue(ctx,sessionKey,c.SessionID)
 next.ServeHTTP(w,r.WithContext(ctx))
})}
func (a *App) listSessions(w http.ResponseWriter,r *http.Request){
 rows,err:=a.db.QueryContext(r.Context(),`SELECT id,created_at,last_seen_at,expires_at FROM sessions WHERE user_id=$1 AND revoked_at IS NULL AND expires_at>NOW() ORDER BY last_seen_at DESC`,uid(r))
 if err!=nil{fail(w,500,"could not load sessions");return}; defer rows.Close()
 out:=make([]map[string]any,0); current,_:=r.Context().Value(sessionKey).(string)
 for rows.Next(){var id string;var created,last,expires time.Time;if rows.Scan(&id,&created,&last,&expires)!=nil{fail(w,500,"could not load sessions");return};out=append(out,map[string]any{"id":id,"created_at":created,"last_seen_at":last,"expires_at":expires,"current":id==current})}
 if rows.Err()!=nil{fail(w,500,"could not load sessions");return};writeJSON(w,200,map[string]any{"sessions":out})
}
func (a *App) revokeSession(w http.ResponseWriter,r *http.Request){
 target:=chi.URLParam(r,"sessionID");if target==""{fail(w,400,"invalid session id");return}
 result,err:=a.db.ExecContext(r.Context(),"UPDATE sessions SET revoked_at=NOW() WHERE id=$1 AND user_id=$2 AND revoked_at IS NULL",target,uid(r))
 if err!=nil{fail(w,500,"could not revoke session");return};n,_:=result.RowsAffected();writeJSON(w,200,map[string]any{"revoked":n>0})
}
func (a *App) logout(w http.ResponseWriter,r *http.Request){
 sid,_:=r.Context().Value(sessionKey).(string)
 if _,err:=a.db.ExecContext(r.Context(),"UPDATE sessions SET revoked_at=NOW() WHERE id=$1 AND user_id=$2 AND revoked_at IS NULL",sid,uid(r));err!=nil{fail(w,500,"could not end session");return}
 writeJSON(w,200,map[string]any{"logged_out":true})
}
func uid(r *http.Request)int64{id,_:=r.Context().Value(userKey).(int64);return id}
type authInput struct{ Username string `json:"username"`; Password string `json:"password"`; DisplayName string `json:"display_name"` }
func validCredentials(w http.ResponseWriter,in authInput)bool{ in.Username=strings.TrimSpace(in.Username); if len(in.Username)<3||len(in.Username)>32||len(in.Password)<10||len(in.Password)>72 {fail(w,400,"username must be 3-32 characters and password 10-72 bytes");return false}; for _,c:=range in.Username{if !(c>='a'&&c<='z'||c>='0'&&c<='9'||c=='_'){fail(w,400,"username may contain lowercase letters, digits and underscore");return false}};return true }
func (a *App) register(w http.ResponseWriter,r *http.Request){var in authInput;if decode(r,&in)!=nil{fail(w,400,"invalid request");return};in.Username=strings.ToLower(strings.TrimSpace(in.Username));if !validCredentials(w,in){return};name:=strings.TrimSpace(in.DisplayName);if name==""{name=in.Username};if len(name)>64{fail(w,400,"display_name is too long");return}; hash,e:=bcrypt.GenerateFromPassword([]byte(in.Password),bcrypt.DefaultCost);if e!=nil{fail(w,500,"could not secure password");return};var id int64;e=a.db.QueryRowContext(r.Context(),"INSERT INTO users(username,display_name,password_hash) VALUES($1,$2,$3) RETURNING id",strings.ToLower(in.Username),name,string(hash)).Scan(&id);if e!=nil{fail(w,409,"username is unavailable");return};token,e:=a.issueToken(id);if e!=nil{fail(w,500,"could not create session");return};writeJSON(w,201,map[string]any{"token":token,"user":map[string]any{"id":id,"username":strings.ToLower(in.Username),"display_name":name}})}
func (a *App) login(w http.ResponseWriter,r *http.Request){var in authInput;if decode(r,&in)!=nil{fail(w,400,"invalid request");return};var id int64;var hash,name string;e:=a.db.QueryRowContext(r.Context(),"SELECT id,password_hash,display_name FROM users WHERE username=$1",strings.ToLower(strings.TrimSpace(in.Username))).Scan(&id,&hash,&name);if e!=nil||bcrypt.CompareHashAndPassword([]byte(hash),[]byte(in.Password))!=nil{fail(w,401,"invalid username or password");return};token,e:=a.issueToken(id);if e!=nil{fail(w,500,"could not create session");return};writeJSON(w,200,map[string]any{"token":token,"user":map[string]any{"id":id,"username":strings.ToLower(in.Username),"display_name":name}})}
func (a *App) me(w http.ResponseWriter,r *http.Request){var id int64;var u,n string;e:=a.db.QueryRowContext(r.Context(),"SELECT id,username,display_name FROM users WHERE id=$1",uid(r)).Scan(&id,&u,&n);if e!=nil{fail(w,404,"user not found");return};writeJSON(w,200,map[string]any{"id":id,"username":u,"display_name":n})}
type conversationInput struct{Kind string `json:"kind"`;Title string `json:"title"`;MemberIDs []int64 `json:"member_ids"`}
func (a *App) createConversation(w http.ResponseWriter,r *http.Request){var in conversationInput;if decode(r,&in)!=nil{fail(w,400,"invalid request");return};if in.Kind!="direct"&&in.Kind!="group"{fail(w,400,"kind must be direct or group");return};if in.Kind=="group"&&strings.TrimSpace(in.Title)==""{fail(w,400,"group title is required");return};members:=map[int64]bool{uid(r):true};for _,id:=range in.MemberIDs{if id>0{members[id]=true}};if in.Kind=="direct"&&len(members)!=2{fail(w,400,"direct conversation requires exactly one other member");return};if len(members)>100{fail(w,400,"group limit is 100 members");return};tx,e:=a.db.BeginTx(r.Context(),nil);if e!=nil{fail(w,500,"database error");return};defer tx.Rollback();var id int64;e=tx.QueryRowContext(r.Context(),"INSERT INTO conversations(kind,title,created_by) VALUES($1,NULLIF($2,''),$3) RETURNING id",in.Kind,strings.TrimSpace(in.Title),uid(r)).Scan(&id);if e!=nil{fail(w,500,"could not create conversation");return};for member:=range members{if _,e=tx.ExecContext(r.Context(),"INSERT INTO conversation_members(conversation_id,user_id) SELECT $1,id FROM users WHERE id=$2",id,member);e!=nil{fail(w,400,"one or more members do not exist");return}};if e=tx.Commit();e!=nil{fail(w,500,"could not save conversation");return};writeJSON(w,201,map[string]any{"id":id,"kind":in.Kind,"title":in.Title})}
func (a *App) listConversations(w http.ResponseWriter,r *http.Request){rows,e:=a.db.QueryContext(r.Context(),`SELECT c.id,c.kind,CASE WHEN c.kind='direct' THEN COALESCE((SELECT u.display_name FROM conversation_members other JOIN users u ON u.id=other.user_id WHERE other.conversation_id=c.id AND other.user_id<>$1 LIMIT 1),'') ELSE COALESCE(c.title,'') END,c.created_at,(SELECT COALESCE(m.body,'') FROM messages m WHERE m.conversation_id=c.id ORDER BY m.id DESC LIMIT 1),(SELECT m.created_at FROM messages m WHERE m.conversation_id=c.id ORDER BY m.id DESC LIMIT 1) FROM conversations c JOIN conversation_members cm ON cm.conversation_id=c.id WHERE cm.user_id=$1 ORDER BY c.id DESC LIMIT 100`,uid(r));if e!=nil{fail(w,500,"database error");return};defer rows.Close();out:=make([]map[string]any,0);for rows.Next(){var id int64;var kind,title,last string;var created time.Time;var lastAt sql.NullTime;if rows.Scan(&id,&kind,&title,&created,&last,&lastAt)!=nil{fail(w,500,"database error");return};item:=map[string]any{"id":id,"kind":kind,"title":title,"created_at":created,"last_message":last};if lastAt.Valid{item["last_message_at"]=lastAt.Time};out=append(out,item)};writeJSON(w,200,out)}
func (a *App) member(r *http.Request,id int64)bool{var ok bool;e:=a.db.QueryRowContext(r.Context(),"SELECT EXISTS(SELECT 1 FROM conversation_members WHERE conversation_id=$1 AND user_id=$2)",id,uid(r)).Scan(&ok);return e==nil&&ok}
func conversationID(r *http.Request)(int64,error){return strconv.ParseInt(chi.URLParam(r,"id"),10,64)}
func (a *App) listMessages(w http.ResponseWriter,r *http.Request){id,e:=conversationID(r);if e!=nil||!a.member(r,id){fail(w,404,"conversation not found");return};limit,_:=strconv.Atoi(r.URL.Query().Get("limit"));if limit<1||limit>100{limit=50};before,_:=strconv.ParseInt(r.URL.Query().Get("before"),10,64);rows,e:=a.db.QueryContext(r.Context(),`SELECT m.id,m.sender_id,u.username,m.body,m.created_at FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.conversation_id=$1 AND ($2=0 OR m.id<$2) ORDER BY m.id DESC LIMIT $3`,id,before,limit);if e!=nil{fail(w,500,"database error");return};defer rows.Close();out:=make([]map[string]any,0);for rows.Next(){var mid,sender int64;var username,body string;var at time.Time;if rows.Scan(&mid,&sender,&username,&body,&at)!=nil{fail(w,500,"database error");return};out=append(out,map[string]any{"id":mid,"sender_id":sender,"username":username,"body":body,"created_at":at})};writeJSON(w,200,out)}
func (a *App) sendMessage(w http.ResponseWriter,r *http.Request){id,e:=conversationID(r);if e!=nil||!a.member(r,id){fail(w,404,"conversation not found");return};var in struct{Body string `json:"body"`};if decode(r,&in)!=nil{fail(w,400,"invalid request");return};in.Body=strings.TrimSpace(in.Body);if len(in.Body)==0||len(in.Body)>10000{fail(w,400,"message must be 1-10000 bytes");return};var mid int64;var at time.Time;e=a.db.QueryRowContext(r.Context(),"INSERT INTO messages(conversation_id,sender_id,body) VALUES($1,$2,$3) RETURNING id,created_at",id,uid(r),in.Body).Scan(&mid,&at);if e!=nil{fail(w,500,"could not send message");return};writeJSON(w,201,map[string]any{"id":mid,"conversation_id":id,"sender_id":uid(r),"body":in.Body,"created_at":at})}
var upgrader=websocket.Upgrader{ReadBufferSize:1024,WriteBufferSize:1024,CheckOrigin:func(r *http.Request)bool{return r.Header.Get("Origin")==""}}
func (a *App) websocket(w http.ResponseWriter,r *http.Request){c,e:=a.parseClaims(r.URL.Query().Get("token"));if e!=nil{fail(w,401,"authentication required");return};var active bool;if e=a.db.QueryRowContext(r.Context(),"SELECT EXISTS(SELECT 1 FROM sessions WHERE id=$1 AND user_id=$2 AND revoked_at IS NULL AND expires_at>NOW())",c.SessionID,c.UserID).Scan(&active);e!=nil||!active{fail(w,401,"session expired or revoked");return};conn,e:=upgrader.Upgrade(w,r,nil);if e!=nil{return};defer conn.Close();_ = conn.WriteJSON(map[string]any{"type":"connected","user_id":c.UserID});for{if _,_,e=conn.ReadMessage();e!=nil{return}}}
