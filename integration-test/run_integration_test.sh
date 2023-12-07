#!/bin/bash

# example: sh ./run_integration_test.sh <local|dev|uat|prod>
# run integration tests
cd ./src || exit
yarn install
yarn test:"$1" || true
echo "Sleeping for 300 seconds (5 minuts) before running teardown"
sleep 300
yarn teardown:"$1"
