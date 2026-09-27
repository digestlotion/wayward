package main

import (
	"log"
	"net/http"
	"os"

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
	mux.HandleFunc("POST /invite", invite)

	log.Println("listening on :8080")
	http.ListenAndServe(":8080", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		lw := &statusWriter{w, 200}
		mux.ServeHTTP(lw, r)
		log.Printf("\033[33m%-4s\033[0m \033[32m%d\033[0m \033[34m%s\033[0m %s",
			r.Method, lw.status, r.RemoteAddr, r.URL.RequestURI(),
		)
	}))
}

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (sw *statusWriter) WriteHeader(code int) {
	sw.status = code
	sw.ResponseWriter.WriteHeader(code)
}
