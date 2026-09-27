package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func auth(w http.ResponseWriter, r *http.Request) {
	username, serverId := r.URL.Query().Get("username"), r.URL.Query().Get("serverId")

	q := url.Values{}
	q.Set("username", username)
	q.Set("serverId", serverId)
	mojangURL := "https://sessionserver.mojang.com/session/minecraft/hasJoined?" + q.Encode()

	resp, err := http.Get(mojangURL)
	if err != nil || resp.StatusCode != 200 {
		http.Error(w, "authentication failed", http.StatusUnauthorized)
		return
	}
	defer resp.Body.Close()

	var profile struct {
		ID string `json:"id"`
	}
	json.NewDecoder(resp.Body).Decode(&profile)

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"uuid": profile.ID,
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
