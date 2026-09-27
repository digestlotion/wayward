package main

import (
	"fmt"
	"net/http"
	"slices"
	"sync"
)

type InviteStore struct {
	mu   sync.RWMutex
	data map[string][]string
}

func NewInviteStore() *InviteStore {
	return &InviteStore{data: make(map[string][]string)}
}

func key(owner, world string) string {
	return fmt.Sprintf("%s/%s", owner, world)
}

func (s *InviteStore) Add(owner, world, friend string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	k := key(owner, world)
	if !slices.Contains(s.data[k], friend) {
		s.data[k] = append(s.data[k], friend)
	}
	println(s.data[k][0])
	println(slices.Contains(s.data[k], friend))
}

func (s *InviteStore) HasAccess(owner, world, requester string) bool {
	if requester == "SUPER" || requester == owner {
		return true
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	return slices.Contains(s.data[key(owner, world)], requester)
}

var invites = NewInviteStore()

func invite(w http.ResponseWriter, r *http.Request) {
	owner, err := authenticate(r)
	if err != nil {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	friend := r.URL.Query().Get("uuid")
	world := r.URL.Query().Get("world")

	if world == "" || friend == "" {
		http.Error(w, "missing world or uuid", http.StatusBadRequest)
		return
	}

	invites.Add(owner, world, friend)
	w.WriteHeader(http.StatusNoContent)

}
