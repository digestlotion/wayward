package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/joho/godotenv"
)

var SECRET string

func main() {
	godotenv.Load()
	SECRET = os.Getenv("SECRET")
	mux := http.NewServeMux()
	mux.HandleFunc("GET /auth", auth)


	http.ListenAndServe(":8080", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
  		log.Printf("%s %s", r.Method, r.URL.Path)
        mux.ServeHTTP(w, r)
	}))
}



func auth(w http.ResponseWriter, r *http.Request) {
	username, serverId := r.URL.Query().Get("username"), r.URL.Query().Get("serverId")
	resp, err := http.Get(
		fmt.Sprintf(
			"https://sessionserver.mojang.com/session/minecraft/hasJoined?username=%s&serverId=%s",
		 	username,
			serverId))

	if err != nil || resp.StatusCode != 200 {
	    http.Error(w, "Authentication failed", http.StatusUnauthorized)
	    return
	}
	defer resp.Body.Close()

	var profile struct {
	    ID   string `json:"id"`
	    Name string `json:"name"`
	}
	json.NewDecoder(resp.Body).Decode(&profile)

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
	    "uuid": profile.ID,
	    "exp":  time.Now().Add(24 * time.Hour).Unix(),
	})

	signed, err := token.SignedString([]byte(SECRET))

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
    	"token": signed,
	})

	}
