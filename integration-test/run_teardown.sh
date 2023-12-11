#!/bin/bash

echo "Sleeping for 300 seconds (5 minutes) before running teardown"
sleep 300
yarn teardown:"$1"