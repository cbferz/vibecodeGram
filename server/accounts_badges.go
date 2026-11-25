package main

import (
 "database/sql"
 "net/http"
 "os"
 "strconv"
 "strings"

 "github.com/go-chi/chi/v5"
)

type badgeDTO struct {
 Code string `json:"code"`
 Title string `json:"title"`
 Description string `json:"description"`
 Color string `json:"color"`
 Icon string `json:"icon"`
 GrantedAt any `json:"granted_at,omitempty"`
}

func (a *App) myBadges(w http.ResponseWriter,r *http.Request) {
 rows,err:=a.db.QueryContext(r.Context(),`SELECT b.code,b.title,b.description,b.color,b.icon,ub.granted_at FROM badges b JOIN user_badges ub ON ub.badge_code=b.code WHERE ub.user_id=$1 ORDER BY b.staff_only DESC,b.premium_only DESC,b.code`,uid(r))
 if err!=nil { fail(w,500,"could not load badges"); return }; defer rows.Close()
 out:=make([]badgeDTO,0)
 for rows.Next(){ var b badgeDTO; if err=rows.Scan(&b.Code,&b.Title,&b.Description,&b.Color,&b.Icon,&b.GrantedAt); err!=nil { fail(w,500,"could not load badges"); return }; out=append(out,b) }
 if rows.Err()!=nil { fail(w,500,"could not load badges"); return }
 writeJSON(w,200,map[string]any{"badges":out})
}


func (a *App) listDeviceAccounts(w http.ResponseWriter,r *http.Request) {
 device:=strings.TrimSpace(r.URL.Query().Get("device_id")); if device==""||len(device)>128 { fail(w,400,"valid device_id is required"); return }
 rows,err:=a.db.QueryContext(r.Context(),`SELECT u.id,u.username,u.display_name,da.added_at,u.is_premium FROM device_accounts da JOIN users u ON u.id=da.account_id WHERE da.device_id=$1 ORDER BY da.added_at`,device)
 if err!=nil { fail(w,500,"could not load device accounts"); return }; defer rows.Close()
 out:=make([]map[string]any,0); limit:=3
 for rows.Next(){ var id int64; var username,name string; var added any; var premium bool; if err=rows.Scan(&id,&username,&name,&added,&premium); err!=nil { fail(w,500,"could not load device accounts"); return }; if premium { limit=5 }; out=append(out,map[string]any{"id":id,"username":username,"display_name":name,"added_at":added,"is_premium":premium}) }
 if rows.Err()!=nil { fail(w,500,"could not load device accounts"); return }
 writeJSON(w,200,map[string]any{"accounts":out,"limit":limit})
}

func (a *App) addDeviceAccount(w http.ResponseWriter,r *http.Request) {
 var in struct { DeviceID string `json:"device_id"` }
 if decode(r,&in)!=nil { fail(w,400,"invalid request"); return }
 in.DeviceID=strings.TrimSpace(in.DeviceID); if in.DeviceID==""||len(in.DeviceID)>128 { fail(w,400,"valid device_id is required"); return }
 userID:=uid(r)
 tx,err:=a.db.BeginTx(r.Context(),nil); if err!=nil { fail(w,500,"database error"); return }; defer tx.Rollback()
 var premium bool
 if err=tx.QueryRowContext(r.Context(),"SELECT is_premium FROM users WHERE id=$1",userID).Scan(&premium); err!=nil { fail(w,404,"user not found"); return }
 limit:=3; if premium { limit=5 }
 var count int
 if err=tx.QueryRowContext(r.Context(),"SELECT COUNT(*) FROM device_accounts WHERE device_id=$1",in.DeviceID).Scan(&count); err!=nil { fail(w,500,"database error"); return }
 var exists bool
 if err=tx.QueryRowContext(r.Context(),"SELECT EXISTS(SELECT 1 FROM device_accounts WHERE device_id=$1 AND account_id=$2)",in.DeviceID,userID).Scan(&exists); err!=nil { fail(w,500,"database error"); return }
 if !exists && count>=limit { fail(w,409,"device account limit reached"); return }
 if _,err=tx.ExecContext(r.Context(),"INSERT INTO device_accounts(device_id,account_id) VALUES($1,$2) ON CONFLICT DO NOTHING",in.DeviceID,userID); err!=nil { fail(w,500,"could not save device account"); return }
 if err=tx.Commit(); err!=nil { fail(w,500,"could not save device account"); return }
 writeJSON(w,200,map[string]any{"added":true,"limit":limit})
}

func (a *App) removeDeviceAccount(w http.ResponseWriter,r *http.Request) {
 device:=strings.TrimSpace(r.URL.Query().Get("device_id")); target,err:=strconv.ParseInt(chi.URLParam(r,"accountID"),10,64)
 if device==""||len(device)>128||err!=nil||target<1 { fail(w,400,"device_id and valid account id are required"); return }
 result,err:=a.db.ExecContext(r.Context(),"DELETE FROM device_accounts WHERE device_id=$1 AND account_id=$2",device,target)
 if err!=nil { fail(w,500,"could not remove device account"); return }; n,_:=result.RowsAffected()
 writeJSON(w,200,map[string]any{"removed":n>0})
}

func (a *App) canManageBadges(actor int64) (bool,bool) {
 founderRaw:=strings.TrimSpace(os.Getenv("FOUNDER_USER_ID")); founderID,_:=strconv.ParseInt(founderRaw,10,64)
 if founderID>0&&actor==founderID { return true,true }
 var role string
 err:=a.db.QueryRow("SELECT badge_code FROM user_badges WHERE user_id=$1 AND badge_code IN ('developer','co_developer') ORDER BY CASE badge_code WHEN 'developer' THEN 0 ELSE 1 END LIMIT 1",actor).Scan(&role)
 if err!=nil { return false,false }; return true,role=="developer"
}

func (a *App) grantBadge(w http.ResponseWriter,r *http.Request) {
 target,err:=strconv.ParseInt(chi.URLParam(r,"id"),10,64); if err!=nil||target<1 { fail(w,400,"invalid user id"); return }
 var in struct { Badge string `json:"badge"` }
 if decode(r,&in)!=nil { fail(w,400,"invalid request"); return }; in.Badge=strings.TrimSpace(in.Badge)
 allowed,full:=a.canManageBadges(uid(r)); if !allowed { fail(w,403,"staff permission required"); return }
 if in.Badge=="founder" { founderRaw:=strings.TrimSpace(os.Getenv("FOUNDER_USER_ID")); founderID,_:=strconv.ParseInt(founderRaw,10,64); if founderID<1||uid(r)!=founderID { fail(w,403,"only the configured founder can grant Founder"); return } }
 if in.Badge=="developer"||in.Badge=="co_developer"||in.Badge=="architect" { if !full { fail(w,403,"only founder or developer can grant this badge"); return } }
 if in.Badge=="team"&&!full { /* co-developers may grant Team */ }
 var premiumOnly bool
 if err=a.db.QueryRowContext(r.Context(),"SELECT premium_only FROM badges WHERE code=$1",in.Badge).Scan(&premiumOnly); err!=nil { fail(w,404,"badge not found"); return }
 if premiumOnly { fail(w,403,"premium badges are assigned by subscription service"); return }
 tx,err:=a.db.BeginTx(r.Context(),nil); if err!=nil { fail(w,500,"database error"); return }; defer tx.Rollback()
 if _,err=tx.ExecContext(r.Context(),"INSERT INTO user_badges(user_id,badge_code,granted_by) VALUES($1,$2,$3) ON CONFLICT DO NOTHING",target,in.Badge,uid(r)); err!=nil { fail(w,404,"user not found or badge unavailable"); return }
 if _,err=tx.ExecContext(r.Context(),"INSERT INTO badge_audit(target_user_id,badge_code,actor_user_id,action) VALUES($1,$2,$3,'grant')",target,in.Badge,uid(r)); err!=nil { fail(w,500,"could not record badge audit"); return }
 if err=tx.Commit(); err!=nil { fail(w,500,"could not grant badge"); return }
 writeJSON(w,200,map[string]any{"granted":true,"user_id":target,"badge":in.Badge})
}

func (a *App) revokeBadge(w http.ResponseWriter,r *http.Request) {
 target,err:=strconv.ParseInt(chi.URLParam(r,"id"),10,64); code:=strings.TrimSpace(chi.URLParam(r,"badge"))
 if err!=nil||target<1 { fail(w,400,"invalid user id"); return }
 allowed,full:=a.canManageBadges(uid(r)); if !allowed { fail(w,403,"staff permission required"); return }
 if code=="founder" { founderRaw:=strings.TrimSpace(os.Getenv("FOUNDER_USER_ID")); founderID,_:=strconv.ParseInt(founderRaw,10,64); if founderID<1||uid(r)!=founderID { fail(w,403,"only the configured founder can revoke Founder"); return } }
 if code=="developer"||code=="co_developer"||code=="architect" { if !full { fail(w,403,"only founder or developer can revoke this badge"); return } }
 if code=="premium"||code=="premium_plus"||code=="premium_elite" { fail(w,403,"premium badges are managed by subscription service"); return }
 tx,err:=a.db.BeginTx(r.Context(),nil); if err!=nil { fail(w,500,"database error"); return }; defer tx.Rollback()
 if _,err=tx.ExecContext(r.Context(),"DELETE FROM user_badges WHERE user_id=$1 AND badge_code=$2",target,code); err!=nil { fail(w,500,"could not revoke badge"); return }
 if _,err=tx.ExecContext(r.Context(),"INSERT INTO badge_audit(target_user_id,badge_code,actor_user_id,action) VALUES($1,$2,$3,'revoke')",target,code,uid(r)); err!=nil { fail(w,500,"could not record badge audit"); return }
 if err=tx.Commit(); err!=nil { fail(w,500,"could not revoke badge"); return }
 writeJSON(w,200,map[string]any{"revoked":true,"user_id":target,"badge":code})
}

func (a *App) subscriptionWebhook(w http.ResponseWriter,r *http.Request) {
 secret:=strings.TrimSpace(os.Getenv("SUBSCRIPTION_WEBHOOK_SECRET"))
 if len(secret)<32 || r.Header.Get("X-Subscription-Secret")!=secret { fail(w,http.StatusUnauthorized,"invalid webhook credentials"); return }
 target,err:=strconv.ParseInt(chi.URLParam(r,"id"),10,64); if err!=nil||target<1 { fail(w,400,"invalid user id"); return }
 var in struct { Tier string `json:"tier"` }
 if decode(r,&in)!=nil { fail(w,400,"invalid request"); return }
 in.Tier=strings.TrimSpace(in.Tier)
 if in.Tier!="none"&&in.Tier!="premium"&&in.Tier!="premium_plus"&&in.Tier!="premium_elite" { fail(w,400,"unsupported premium tier"); return }
 tx,err:=a.db.BeginTx(r.Context(),nil); if err!=nil { fail(w,500,"database error"); return }; defer tx.Rollback()
 premium:=in.Tier!="none"
 if _,err=tx.ExecContext(r.Context(),"UPDATE users SET is_premium=$1,premium_tier=$2 WHERE id=$3",premium,in.Tier,target); err!=nil { fail(w,404,"user not found"); return }
 if _,err=tx.ExecContext(r.Context(),"DELETE FROM user_badges WHERE user_id=$1 AND badge_code IN ('premium','premium_plus','premium_elite')",target); err!=nil { fail(w,500,"could not update premium badges"); return }
 if premium { if _,err=tx.ExecContext(r.Context(),"INSERT INTO user_badges(user_id,badge_code) VALUES($1,'premium'),($1,$2)",target,in.Tier); err!=nil { fail(w,500,"could not assign premium badges"); return } }
 if err=tx.Commit(); err!=nil { fail(w,500,"could not update subscription"); return }
 writeJSON(w,200,map[string]any{"updated":true,"user_id":target,"tier":in.Tier,"is_premium":premium})
}
