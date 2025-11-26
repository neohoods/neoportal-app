#!/bin/bash
echo "Testing Docker connection..."

# Test 1: Standard docker command
echo "1. Testing 'docker ps':"
docker ps > /dev/null 2>&1 && echo "   ✅ docker ps works" || echo "   ❌ docker ps failed"

# Test 2: Docker socket at ~/.docker/run/docker.sock
echo "2. Testing socket at ~/.docker/run/docker.sock:"
if [ -S "$HOME/.docker/run/docker.sock" ]; then
    curl -s --unix-socket "$HOME/.docker/run/docker.sock" http://localhost/version > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "   ✅ Socket works"
    else
        echo "   ❌ Socket exists but connection failed"
    fi
else
    echo "   ❌ Socket not found"
fi

# Test 3: Docker Desktop socket
echo "3. Testing Docker Desktop socket:"
if [ -S "$HOME/Library/Containers/com.docker.docker/Data/docker-cli.sock" ]; then
    curl -s --unix-socket "$HOME/Library/Containers/com.docker.docker/Data/docker-cli.sock" http://localhost/version > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "   ✅ Socket works"
    else
        echo "   ❌ Socket exists but connection failed"
    fi
else
    echo "   ❌ Socket not found"
fi

# Test 4: Check DOCKER_HOST
echo "4. Current DOCKER_HOST: ${DOCKER_HOST:-not set}"
