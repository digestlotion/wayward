package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

var (
	nonceMu    sync.Mutex
	nonces     = map[string]time.Time{}
	httpClient = &http.Client{Timeout: 10 * time.Second}
)

func auth(w http.ResponseWriter, r *http.Request) {
	if r.URL.RawQuery == "" {
		b := make([]byte, 16)
		rand.Read(b)
		nonce := hex.EncodeToString(b)
		nonceMu.Lock()
		for k, exp := range nonces {
			if time.Now().After(exp) {
				delete(nonces, k)
			}
		}
		nonces[nonce] = time.Now().Add(2 * time.Minute)
		nonceMu.Unlock()

		w.Write([]byte(nonce))
		return
	}

	username, serverId := r.URL.Query().Get("username"), r.URL.Query().Get("serverId")

	nonceMu.Lock()
	exp, ok := nonces[serverId]
	delete(nonces, serverId)
	nonceMu.Unlock()
	if !ok || time.Now().After(exp) {
		http.Error(w, "authentication failed", http.StatusUnauthorized)
		return
	}

	q := url.Values{}
	q.Set("username", username)
	q.Set("serverId", serverId)
	mojangURL := "https://sessionserver.mojang.com/session/minecraft/hasJoined?" + q.Encode()

	resp, err := httpClient.Get(mojangURL)
	if err != nil {
		http.Error(w, "authentication failed", http.StatusUnauthorized)
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		http.Error(w, "authentication failed", http.StatusUnauthorized)
		return
	}

	var profile struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&profile); err != nil {
		http.Error(w, "authentication failed", http.StatusUnauthorized)
		return
	}

	u, err := uuid.Parse(profile.ID)
	if err != nil {
		http.Error(w, "authentication failed", http.StatusUnauthorized)
		return
	}
	uuid := u.String()

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"uuid": uuid,
		"exp":  time.Now().Add(24 * time.Hour).Unix(),
	})
	signed, err := token.SignedString([]byte(SECRET))
	if err != nil {
		http.Error(w, "failed to sign token", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"token": signed})
}

func authenticate(r *http.Request) (string, error) {
	tokenStr := r.Header.Get("Authorization")
	if tokenStr == SECRET {
		return "SUPER", nil
	}
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (any, error) {
		return []byte(SECRET), nil
	}, jwt.WithValidMethods([]string{"HS256"}))
	if err != nil || !token.Valid {
		return "", fmt.Errorf("invalid token")
	}
	claims := token.Claims.(jwt.MapClaims)
	uuid, ok := claims["uuid"].(string)
	if !ok {
		return "", fmt.Errorf("invalid token claims")
	}
	return uuid, nil
}

func authorizeRequest(w http.ResponseWriter, r *http.Request) (u, path string, ok bool) {
	requester, err := authenticate(r)
	if err != nil {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return "", "", false
	}

	parsed, err := uuid.Parse(r.PathValue("uuid"))
	if err != nil {
		http.Error(w, "invalid uuid", http.StatusBadRequest)
		return "", "", false
	}
	u = parsed.String()
	path = strings.TrimPrefix(filepath.ToSlash(filepath.Clean("/"+r.PathValue("path"))), "/")

	if !invites.HasAccess(u, worldOf(path), requester) {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return "", "", false
	}

	return u, path, true
}
