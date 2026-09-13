package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/joho/godotenv"
)

var SECRET string

func main() {
	godotenv.Load()
	SECRET = os.Getenv("SECRET")
	if SECRET == "" {
		log.Fatal("SECRET env var not set")
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /auth", auth)
	mux.HandleFunc("POST /manifest/{uuid}/{path...}", manifest)
	mux.HandleFunc("POST /files/{uuid}/{path...}", uploadFile)
	mux.HandleFunc("GET /files/{uuid}/{path...}", downloadFile)

	log.Println("listening on :8080")
	http.ListenAndServe(":8080", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		log.Printf("\033[33m%-4s\033[0m \033[34m%s\033[0m",
			r.Method, r.URL.Path,
		)
		mux.ServeHTTP(w, r)
	}))
}

func auth(w http.ResponseWriter, r *http.Request) {
	username, serverId := r.URL.Query().Get("username"), r.URL.Query().Get("serverId")
	resp, err := http.Get(fmt.Sprintf(
		"https://sessionserver.mojang.com/session/minecraft/hasJoined?username=%s&serverId=%s",
		username, serverId))
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

func verifyToken(r *http.Request) (string, error) {
	tokenStr := r.Header.Get("Authorization")
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (any, error) {
		return []byte(SECRET), nil
	})
	if err != nil || !token.Valid {
		return "", fmt.Errorf("invalid token")
	}
	claims := token.Claims.(jwt.MapClaims)
	return claims["uuid"].(string), nil
}

func safePath(uuid, rel string) (string, error) {
	base := filepath.Join("data", uuid)
	full := filepath.Join(base, filepath.FromSlash(rel))
	if !strings.HasPrefix(full, base) {
		return "", fmt.Errorf("invalid path")
	}
	return full, nil
}

func hashFile(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:]), nil
}

func manifest(w http.ResponseWriter, r *http.Request) {
	if _, err := verifyToken(r); err != nil {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	uuid := r.PathValue("uuid")
	worldPath := r.PathValue("path")

	var clientManifest map[string]string
	if err := json.NewDecoder(r.Body).Decode(&clientManifest); err != nil {
		http.Error(w, "invalid manifest", http.StatusBadRequest)
		return
	}

	diff := []string{}

	for rel, clientHash := range clientManifest {
		full, err := safePath(uuid, filepath.Join(worldPath, rel))
		if err != nil {
			continue
		}
		serverHash, err := hashFile(full)
		if err != nil || serverHash != clientHash {
			diff = append(diff, rel)
		}
	}

	base, _ := safePath(uuid, worldPath)
	filepath.Walk(base, func(path string, info os.FileInfo, err error) error {
		if err != nil || info.IsDir() {
			return nil
		}
		rel := filepath.ToSlash(strings.TrimPrefix(path, base+string(filepath.Separator)))
		if _, exists := clientManifest[rel]; !exists {
			diff = append(diff, rel)
		}
		return nil
	})

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(diff)
}

func uploadFile(w http.ResponseWriter, r *http.Request) {
	if _, err := verifyToken(r); err != nil {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	uuid := r.PathValue("uuid")
	rel := r.PathValue("path")

	full, err := safePath(uuid, rel)
	if err != nil {
		http.Error(w, "invalid path", http.StatusBadRequest)
		return
	}

	os.MkdirAll(filepath.Dir(full), 0755)
	data, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "failed to read body", http.StatusInternalServerError)
		return
	}

	os.WriteFile(full, data, 0644)
	w.WriteHeader(http.StatusNoContent)
}

func downloadFile(w http.ResponseWriter, r *http.Request) {
	if _, err := verifyToken(r); err != nil {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	uuid := r.PathValue("uuid")
	rel := r.PathValue("path")

	full, err := safePath(uuid, rel)
	if err != nil {
		http.Error(w, "invalid path", http.StatusBadRequest)
		return
	}

	data, err := os.ReadFile(full)
	if err != nil {
		http.Error(w, "file not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/octet-stream")
	w.Write(data)
}
