#!/usr/bin/env bash
protoc --python_out=../python-agent/ src/main/proto/grpc_messages.proto