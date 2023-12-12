#!/bin/bash

echo "Sleeping for 120 seconds (2 minutes) before running teardown"
sleep 120

cd ./src || exit
yarn install
yarn teardown:"$1"