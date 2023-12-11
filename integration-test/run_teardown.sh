#!/bin/bash

echo "Sleeping for 300 seconds (5 minutes) before running teardown"
sleep 300

cd ./src || exit
yarn install
yarn teardown:"$1"