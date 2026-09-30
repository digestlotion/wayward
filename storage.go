package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strings"
)

var allowedFiles = []string{".dat", ".dat_old", ".mca", ".json", ".png"}

func safePath(uuid, rel string) (string, error) {
	base := filepath.Join("data", uuid)
	full := filepath.Join(base, filepath.FromSlash(rel))
	if full != base && !strings.HasPrefix(full, base+string(filepath.Separator)) {
		return "", fmt.Errorf("invalid path")
	}
	return full, nil
}

func hashFile(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

func worldOf(path string) string {
	if i := strings.Index(path, "/"); i != -1 {
		return path[:i]
	}
	return path
}

func manifest(w http.ResponseWriter, r *http.Request) {
	uuid, worldPath, ok := authorizeRequest(w, r)
	if !ok {
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, 1*1024*1024)
	var clientManifest map[string]string
	if err := json.NewDecoder(r.Body).Decode(&clientManifest); err != nil {
		http.Error(w, "invalid or too large manifest", http.StatusBadRequest)
		return
	}

	diff := []string{}

	base, _ := safePath(uuid, worldPath)

	for rel, clientHash := range clientManifest {
		full, err := safePath(uuid, filepath.Join(worldPath, rel))
		if err != nil || !strings.HasPrefix(full, base+string(filepath.Separator)) {
			continue
		}
		serverHash, err := hashFile(full)
		if err != nil || serverHash != clientHash {
			diff = append(diff, rel)
		}
	}

	filepath.WalkDir(base, func(path string, d os.DirEntry, err error) error {
		if err != nil || d.IsDir() || strings.HasPrefix(d.Name(), ".upload-") {
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
	uuid, rel, ok := authorizeRequest(w, r)
	if !ok {
		return
	}

	if !slices.Contains(allowedFiles, filepath.Ext(rel)) {
		http.Error(w, "file type not allowed", http.StatusBadRequest)
		return
	}

	full, err := safePath(uuid, rel)
	if err != nil {
		http.Error(w, "invalid path", http.StatusBadRequest)
		return
	}

	if err := os.MkdirAll(filepath.Dir(full), 0755); err != nil {
		http.Error(w, "failed to write file", http.StatusInternalServerError)
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, 50*1024*1024)
	data, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "file too large or failed to read body", http.StatusRequestEntityTooLarge)
		return
	}

	tmp, err := os.CreateTemp(filepath.Dir(full), ".upload-*")
	if err != nil {
		http.Error(w, "failed to write file", http.StatusInternalServerError)
		return
	}
	_, err = tmp.Write(data)
	if cerr := tmp.Close(); err == nil {
		err = cerr
	}
	if err == nil {
		err = os.Chmod(tmp.Name(), 0644)
	}
	if err == nil {
		err = os.Rename(tmp.Name(), full)
	}
	if err != nil {
		os.Remove(tmp.Name())
		http.Error(w, "failed to write file", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func downloadFile(w http.ResponseWriter, r *http.Request) {
	uuid, rel, ok := authorizeRequest(w, r)
	if !ok {
		return
	}

	full, err := safePath(uuid, rel)
	if err != nil {
		http.Error(w, "invalid path", http.StatusBadRequest)
		return
	}

	f, err := os.Open(full)
	if err != nil {
		http.Error(w, "file not found", http.StatusNotFound)
		return
	}
	defer f.Close()
	if info, err := f.Stat(); err != nil || info.IsDir() {
		http.Error(w, "file not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/octet-stream")
	io.Copy(w, f)
}
