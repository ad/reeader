#!/bin/sh
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
UA="Reeader-smoke"
J() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" 2>&1 | head -c 300; echo; }

greader() {
  name=$1 base=$2 user=$3 pass=$4; shift 4
  [ -n "$pass" ] || { echo "== $name: skipped"; return; }
  echo "== $name"
  auth=$(curl -s "$@" --data-urlencode "Email=$user" --data-urlencode "Passwd=$pass" "$base/accounts/ClientLogin" | sed -n 's/^Auth=//p')
  [ -n "$auth" ] || { echo "login FAILED"; return; }
  h="Authorization: GoogleLogin auth=$auth"
  curl -s "$@" -H "$h" "$base/reader/api/0/subscription/list?output=json" | J '"subs:", len(d["subscriptions"]), d["subscriptions"][:1] and d["subscriptions"][0]["id"]'
  ids=$(curl -s "$@" -H "$h" "$base/reader/api/0/stream/items/ids?output=json&n=3&s=user/-/state/com.google/reading-list" | python3 -c "import sys,json; print(' '.join(r['id'] for r in json.load(sys.stdin).get('itemRefs',[])))")
  echo "itemRefs ids: $ids"
  long=$(python3 -c "
import sys
P='tag:google.com,2005:reader/item/'
for i in sys.argv[1:]:
    try: print(P + format(int(i) & (2**64 - 1), '016x'))
    except ValueError: print(P + i)" $ids)
  tok=$(curl -s "$@" -H "$h" "$base/reader/api/0/token")
  form=""; for i in $long; do form="$form&i=$(python3 -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$i")"; done
  [ -n "$form" ] && curl -s "$@" -H "$h" -d "T=$tok$form" "$base/reader/api/0/stream/items/contents?output=json" | python3 -c "
import sys,json
want=sys.argv[1].split()
got=[i['id'] for i in json.load(sys.stdin).get('items',[])]
print('content ids:', got)
print('ids match:', sorted(want) == sorted(got))" "$long"
}

greader FreshRSS "${FRESHRSS_URL%/}/api/greader.php" "$FRESHRSS_USER" "$FRESHRSS_PASS"
greader BazQux https://bazqux.com "$BAZQUX_USER" "$BAZQUX_PASS"
greader "The Old Reader" https://theoldreader.com "$OLDREADER_USER" "$OLDREADER_PASS"
greader Inoreader https://www.inoreader.com "$INOREADER_USER" "$INOREADER_PASS" -H "AppId: $INOREADER_APP_ID" -H "AppKey: $INOREADER_APP_KEY"

if [ -n "$FEEDBIN_PASS" ]; then
  echo "== Feedbin"
  fb() { curl -s -u "$FEEDBIN_USER:$FEEDBIN_PASS" "https://api.feedbin.com/v2/$1"; }
  curl -s -o /dev/null -w "auth: %{http_code}\n" -u "$FEEDBIN_USER:$FEEDBIN_PASS" https://api.feedbin.com/v2/authentication.json
  fb subscriptions.json | J '"subs:", len(d), d[:1] and {k: d[0][k] for k in ("feed_id","site_url")}'
  fb unread_entries.json | J '"unread:", len(d), d[:3]'
  id=$(fb unread_entries.json | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0] if d else '')")
  [ -n "$id" ] && fb "entries.json?ids=$id" | J '{k: d[0].get(k) for k in ("id","feed_id","published","url")}'
fi

if [ -n "$NEWSBLUR_PASS" ]; then
  echo "== NewsBlur"
  jar=$(mktemp)
  curl -s -A "$UA" -c "$jar" --data-urlencode "username=$NEWSBLUR_USER" --data-urlencode "password=$NEWSBLUR_PASS" https://www.newsblur.com/api/login | J '"login code:", d.get("code"), d.get("errors")'
  echo "cookie set: $(grep -c newsblur_sessionid "$jar")"
  nb() { curl -s -A "$UA" -b "$jar" "https://www.newsblur.com$1"; }
  nb "/reader/feeds?flat=true&update_counts=false" | J '"authenticated:", d.get("authenticated"), "feeds:", len(d.get("feeds",{})), "folders:", list(d.get("flat_folders",{}))[:3]'
  nb /reader/unread_story_hashes | J '"unread sample:", [h for v in d["unread_feed_story_hashes"].values() for h in v][:3]'
  nb /reader/starred_story_hashes | J '"starred:", d.get("starred_story_hashes", [])[:3]'
  rm -f "$jar"
fi

if [ -n "$FEEDLY_TOKEN" ]; then
  echo "== Feedly"
  fl() { curl -s -H "Authorization: Bearer $FEEDLY_TOKEN" "https://cloud.feedly.com/v3/$1"; }
  uid=$(fl profile | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))")
  echo "profile id present: $([ -n "$uid" ] && echo yes || echo no)"
  fl subscriptions | J '"subs:", len(d), d[:1] and {k: d[0].get(k) for k in ("id","website","iconUrl","visualUrl")}'
  fl "streams/ids?count=3&unreadOnly=true&streamId=user/$uid/category/global.all" | J '"unread ids:", d.get("ids")'
fi
