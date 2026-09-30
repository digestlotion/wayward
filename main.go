package main

import (
	"log"
	"net"
	"net/http"
	"os"
	"strings"
	"time"

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

	srv := &http.Server{
		Addr:              "",
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       5 * time.Minute,
		WriteTimeout:      5 * time.Minute,
		IdleTimeout:       2 * time.Minute,
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			lw := &statusWriter{w, 200}
			mux.ServeHTTP(lw, r)
			ip, _, err := net.SplitHostPort(r.RemoteAddr)
			if err != nil {
				ip = r.RemoteAddr
			}
			if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
				ip = strings.TrimSpace(strings.Split(xff, ",")[0])
			}
			log.Printf("\033[33m%-4s\033[0m \033[32m%d\033[0m \033[34m%s\033[0m %s",
				r.Method, lw.status, ip, r.URL.RequestURI(),
			)
		}),
	}
	log.Fatal(srv.ListenAndServe())
}

type statusWriter struct {
	http.ResponseWriter
	status int
}

func (sw *statusWriter) WriteHeader(code int) {
	sw.status = code
	sw.ResponseWriter.WriteHeader(code)
}

func (sw *statusWriter) Flush() {
	if f, ok := sw.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

func (sw *statusWriter) Unwrap() http.ResponseWriter {
	return sw.ResponseWriter
}
