# pagoPA Receipt-pdf-helpdesk

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=pagopa_pagopa-receipt-pdf-helpdesk&metric=alert_status)](https://sonarcloud.io/dashboard?id=pagopa_pagopa-receipt-pdf-helpdesk)

Java Azure Functions that exposed the following recover APIs:
- GetCart
- GetReceipt
- GetReceiptByOrganizationFiscalCodeAndIUV
- GetReceiptError
- GetReceiptMessage
- GetReceiptPdf
- ReceiptToReviewed
- RecoverFailedReceipt
- RecoverFailedReceiptMassive
- RecoverFailedReceiptScheduled
- RecoverNotNotifiedReceipt
- RecoverNotNotifiedReceiptMassive
- RecoverNotNotifiedReceiptScheduled
- RegenerateReceiptPdf

---

## Summary 📖

- [Api Documentation 📖](#api-documentation-)
- [Start Project Locally 🚀](#start-project-locally-)
  * [Run locally with Docker](#run-locally-with-docker)
    + [Prerequisites](#prerequisites)
    + [Run docker container](#run-docker-container)
  * [Run locally with Maven](#run-locally-with-maven)
    + [Prerequisites](#prerequisites-1)
    + [Set environment variables](#set-environment-variables)
    + [Run the project](#run-the-project)
  * [Test](#test)
- [Develop Locally 💻](#develop-locally-)
  * [Prerequisites](#prerequisites-2)
  * [Testing 🧪](#testing-)
    + [Unit testing](#unit-testing)
    + [Integration testing](#integration-testing)
    + [Performance testing](#performance-testing)
- [Contributors 👥](#contributors-)
  * [Maintainers](#maintainers)

---

## Api Documentation 📖

See
the [OpenApi 3 here](https://editor.swagger.io/?url=https://raw.githubusercontent.com/pagopa/pagopa-receipt-pdf-helpdesk/main/openapi/openapi.json)

## Start Project Locally 🚀

### Run locally with Docker

#### Prerequisites

- docker

#### Set environment variables

`docker build -t pagopa-receip-pdf-helpdesk .`

`cp .env.example .env`

and replace in `.env` with correct values

#### Run docker container

then type :

`docker run -p 80:80 --env-file=./.env pagopa-receip-pdf-helpdesk`

### Run locally with Maven

#### Prerequisites

- maven

#### Set environment variables

On terminal type:

`cp local.settings.json.example local.settings.json`

then replace env variables with correct values
(if there is NO default value, the variable HAS to be defined)

| VARIABLE                                | USAGE                                                                                                                                                |                     DEFAULT VALUE                      |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------:|
| `RECEIPTS_STORAGE_CONN_STRING`          | Connection string to the Receipt Queue                                                                                                               |                                                        |
| `RECEIPT_QUEUE_TOPIC`                   | Topic name of the Receipt Queue                                                                                                                      |                                                        |
| `COSMOS_BIZ_EVENT_CONN_STRING`          | Connection string to the BizEvent CosmosDB                                                                                                           |                                                        |
| `COSMOS_BIZ_EVENT_SERVICE_ENDPOINT`     | Endpoint to the BizEvent CosmosDB                                                                                                                    |                                                        |
| `COSMOS_BIZ_EVENT_DB_NAME`              | Database name of the BizEvent database in CosmosDB                                                                                                   |                                                        |
| `COSMOS_BIZ_EVENT_CONTAINER_NAME`       | Container name of the BizEvent container in CosmosDB                                                                                                 |                                                        |
| `COSMOS_RECEIPTS_CONN_STRING`           | Connection string to the Receipt CosmosDB                                                                                                            |                                                        |
| `COSMOS_RECEIPT_SERVICE_ENDPOINT`       | Endpoint to the Receipt CosmosDB                                                                                                                     |                                                        |
| `COSMOS_RECEIPT_KEY`                    | Key to the Receipt CosmosDB                                                                                                                          |                                                        |
| `COSMOS_RECEIPT_DB_NAME`                | Database name of the Receipt database in CosmosDB                                                                                                    |                                                        |
| `COSMOS_RECEIPT_CONTAINER_NAME`         | Container name of the Receipt container in CosmosDB                                                                                                  |                                                        |
| `COSMOS_RECEIPT_ERROR_CONTAINER_NAME`   | Container name of the receipt-message-error container in CosmosDB                                                                                    |                                                        |
| `COSMOS_RECEIPT_MESSAGE_CONTAINER_NAME` | Container name of the receipts-io-messages container in CosmosDB                                                                                     |                                                        |
| `COSMOS_RECEIPT_CART_CONTAINER_NAME`    | Container name of the cart-for-receipts container in CosmosDB                                                                                        |                                                        |
| `BLOB_STORAGE_ACCOUNT_ENDPOINT`         | Endpoint to the Receipt Blob Storage                                                                                                                 |                                                        |
| `BLOB_STORAGE_CONTAINER_NAME`           | Container name of the Blob Storage containing the pdf attachments                                                                                    |                                                        |
| `BLOB_STORAGE_DOWNLOAD_TIMEOUT`         | Timeout for the call to retrieve the attachment from the blob storage                                                                                |                           10                           |
| `BLOB_STORAGE_DOWNLOAD_MAX_RETRY`       | Max number of retry for the call to retrieve the attachment from the blob storage                                                                    |                           5                            |
| `PDV_TOKENIZER_BASE_PATH`               | PDV Tokenizer API base path                                                                                                                          | "https://api.uat.tokenizer.pdv.pagopa.it/tokenizer/v1" |
| `PDV_TOKENIZER_SEARCH_TOKEN_ENDPOINT`   | PDV Tokenizer API search token endpoint                                                                                                              |                    "/tokens/search"                    |
| `PDV_TOKENIZER_FIND_PII_ENDPOINT`       | PDV Tokenizer API find pii endpoint                                                                                                                  |                    "/tokens/%s/pii"                    |
| `PDV_TOKENIZER_CREATE_TOKEN_ENDPOINT`   | PDV Tokenizer API create token endpoint                                                                                                              |                       "/tokens"                        |
| `PDV_TOKENIZER_SUBSCRIPTION_KEY`        | API azure ocp apim subscription key                                                                                                                  |                                                        |
| `PDV_TOKENIZER_INITIAL_INTERVAL`        | PDV Tokenizer initial interval for retry a request that fail with 429 status code                                                                    |                          200                           |
| `PDV_TOKENIZER_MULTIPLIER`              | PDV Tokenizer interval multiplier for subsequent request retry                                                                                       |                          2.0                           |
| `PDV_TOKENIZER_RANDOMIZATION_FACTOR`    | PDV Tokenizer randomization factor for interval retry calculation                                                                                    |                          0.6                           |
| `PDV_TOKENIZER_MAX_RETRIES`             | PDV Tokenizer max request retry                                                                                                                      |                           3                            |
| `TOKENIZER_APIM_HEADER_KEY`             | Tokenizer APIM header key                                                                                                                            |                       x-api-key                        |
| `MAX_DATE_DIFF_MILLIS`                  | Difference in millis between the current time and the date from witch the<br/> receipts will be fetched in massive recover operation                 |                         360000                         |
| `MAX_DATE_DIFF_NOTIFY_MILLIS`           | Difference in millis between the current time and the date from witch the<br/> receipts will be fetched in massive recover operation on notification |                         360000                         |
| `RECOVER_FAILED_CRON`                   | CRON expression for timer trigger function that recover failed receipt                                                                               |                                                        |
| `TRIGGER_NOTIFY_REC_SCHEDULE`           | CRON expression for timer trigger function that recover not notifier receipt                                                                         |                                                        |
| `RECOVER_FAILED_MASSIVE_MAX_DAYS`       | Number of days in addition to the current one to executed failed recovery                                                                            |                           0                            |
| `RECOVER_NOT_NOTIFIED_MASSIVE_MAX_DAYS` | Number of days in addition to the current one to executed not notified recovery                                                                      |                           0                            |
| `AES_SECRET_KEY`                        | AES encryption secret key                                                                                                                            |                                                        |
| `AES_SALT`                              | AES encryption salt                                                                                                                                  |

> to doc details about AZ fn config
> see [here](https://stackoverflow.com/questions/62669672/azure-functions-what-is-the-purpose-of-having-host-json-and-local-settings-jso)


#### Run the project

`mvn clean package`

`mvn azure-functions:run`

### Test

`curl http://localhost:8080/info`

---

## Develop Locally 💻

### Prerequisites

- git
- maven
- jdk-17

### Testing 🧪

#### Unit testing

To run the **Junit** tests:

`mvn clean verify`

#### Integration testing

#### Performance testing

---

## Contributors 👥

Made with ❤️ by PagoPa S.p.A.

### Maintainers

See `CODEOWNERS` file