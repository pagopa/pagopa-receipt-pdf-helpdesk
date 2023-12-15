const TOKENIZED_FISCAL_CODE = "cd07268c-73e8-4df4-8305-a35085e32eff";
const FISCAL_CODE = "AAAAAA00A00A000A";

function sleep(ms) {
	return new Promise(resolve => setTimeout(resolve, ms));
}

function getRandomInt(min, max) {
	min = Math.ceil(min);
	max = Math.floor(max);
	return Math.floor(Math.random() * (max - min) + min); // The maximum is exclusive and the minimum is inclusive
  }
  

function createEvent(id, status, orgCode, iuv) {
	let json_event = {
		"id": id,
		"version": "2",
		"idPaymentManager": "54927408",
		"complete": "false",
		"receiptId": "9851395f09544a04b288202299193ca6",
		"missingInfo": [
			"psp.pspPartitaIVA",
			"paymentInfo.primaryCiIncurredFee",
			"paymentInfo.idBundle",
			"paymentInfo.idCiBundle"
		],
		"debtorPosition": {
			"modelType": "2",
			"noticeNumber": "310391366991197059",
			"iuv": iuv || "iuv"
		},
		"creditor": {
			"idPA": orgCode || "orgCode",
			"idBrokerPA": "66666666666",
			"idStation": "66666666666_08",
			"companyName": "PA paolo",
			"officeName": "office"
		},
		"psp": {
			"idPsp": "BNLIITRR",
			"idBrokerPsp": "60000000001",
			"idChannel": "60000000001_08",
			"psp": "PSP Paolo",
			"pspFiscalCode": "CF60000000006",
			"channelDescription": "app"
		},
		"debtor": {
			"fullName": "paGetPaymentName",
			"entityUniqueIdentifierType": "G",
			"entityUniqueIdentifierValue": FISCAL_CODE,
			"streetName": "paGetPaymentStreet",
			"civicNumber": "paGetPayment99",
			"postalCode": "20155",
			"city": "paGetPaymentCity",
			"stateProvinceRegion": "paGetPaymentState",
			"country": "IT",
			"eMail": "paGetPayment@test.it"
		},
		"payer": {
			"fullName": "name",
			"entityUniqueIdentifierType": "G",
			"entityUniqueIdentifierValue": FISCAL_CODE,
			"streetName": "street",
			"civicNumber": "civic",
			"postalCode": "postal",
			"city": "city",
			"stateProvinceRegion": "state",
			"country": "IT",
			"eMail": "prova@test.it"
		},
		"paymentInfo": {
			"paymentDateTime": "2023-03-17T16:37:36.955813",
			"applicationDate": "2021-12-12",
			"transferDate": "2021-12-11",
			"dueDate": "2021-12-12",
			"paymentToken": "9851395f09544a04b288202299193ca6",
			"amount": "10.0",
			"fee": "2.0",
			"totalNotice": "1",
			"paymentMethod": "creditCard",
			"touchpoint": "app",
			"remittanceInformation": "TARI 2021",
			"description": "TARI 2021",
			"metadata": [
				{
					"key": "1",
					"value": "22"
				}
			]
		},
		"transferList": [
			{
				"idTransfer": "1",
				"fiscalCodePA": "66666666666",
				"companyName": "PA paolo",
				"amount": "10.0",
				"transferCategory": "paGetPaymentTest",
				"remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
			}
		],
		"transactionDetails": {
			"user": {
				"fullName": "John Doe",
				"type": "F",
				"fiscalCode": FISCAL_CODE,
				"notificationEmail": "john.doe@mail.it",
				"userId": "1234",
				"userStatus": "11",
				"userStatusDescription": "REGISTERED_SPID"
			},
			"transaction": {
				"idTransaction": 123456,
				"transactionId": 123456,
				"grandTotal": 0,
				"amount": 0,
				"fee": 0
			}
		},
		"timestamp": 1679067463501,
		"properties": {
			"diagnostic-id": "00-f70ef3167cffad76c6657a67a33ee0d2-61d794a75df0b43b-01",
			"serviceIdentifier": "NDP002SIT"
		},
		"eventStatus": status || "DONE",
		"eventRetryEnrichmentCount": 0
	}
	return json_event
}

function createReceipt(id, status) {
    currentDate = new Date();
	let receipt =
	{
		"eventId": id,
		"eventData": {
			"payerFiscalCode": TOKENIZED_FISCAL_CODE,
			"debtorFiscalCode": TOKENIZED_FISCAL_CODE,
			"amount": "200",
			"cart": [
				{
					"payeeName": "Comune di Milano",
					"subject": "ACI"
				}
			]
		},
		"status": status || "INSERTED",
		"numRetry": 0,
		"inserted_at": currentDate.getTime() - 360000,
		"generated_at" currentDate.getTime() - 360000,
		"id": id
	}
	if (status === "IO_NOTIFIED" || status === "GENERATED") {
		receipt.mdAttach = { "name": "helpdesk-pdf-p.pdf", "url": "helpdesk-pdf-p.pdf" };
	}
	return receipt
}

const getTokenizedBizEvent = () => {
	let environment = process.env.ENVIRONMENT || "";
	if (environment === "uat") {
		return "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";
	}

	return "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";
}
const TOKENIZED_BIZ_EVENT = getTokenizedBizEvent();
function createReceiptError(id, status) {
	return {
		"id": id,
		"bizEventId": id,
		"messagePayload": TOKENIZED_BIZ_EVENT,
		"messageError": "Unexpected error when decrypting the given string",
		"status": status || "TO_REVIEW",
	}
}

module.exports = {
	TOKENIZED_FISCAL_CODE,
	createEvent,
	sleep,
	createReceipt,
	createReceiptError,
	getRandomInt
}