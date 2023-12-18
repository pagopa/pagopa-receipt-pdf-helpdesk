const fs = require('fs');

const { postTokenPii, postPdfRigenerate } = require("./io_client");
const { getReceiptsByCf, updateBizStatusToRETRY, getBiz } = require("./utils");


let currentDate = new Date()
let yesterday = new Date(currentDate)
yesterday.setDate(yesterday.getDate() - 1)

function padTo2Digits(num) {
  return num.toString().padStart(2, '0');
}

function formatDate(date) {
  return (
    [
      date.getFullYear(),
      padTo2Digits(date.getMonth() + 1),
      padTo2Digits(date.getDate()),
    ].join('-')
  );
}

yesterday_ = formatDate(yesterday);

const cf_bsimul = [
  '<CF_1>',
  '<CF_2">',
];


// tokenizer
const PDV_TOKENIZER_BASE_PATH = "https://api.tokenizer.pdv.pagopa.it/tokenizer/v1/"
const customHeaders_ = {
  "Content-Type": "application/json",
  "x-api-key": "<x-api-key>"
}

// const PDV_TOKENIZER_BASE_PATH = "https://api.uat.tokenizer.pdv.pagopa.it/tokenizer/v1/"
// const customHeaders_ = {
//   "Content-Type": "application/json",
//   "x-api-key": "<x-api-key>"
// }



// pdf help
const PDF_HELP_BASE_PATH = "https://api.platform.pagopa.it/receipts/helpdesk/v1/"
const customHeadersPdf_ = {
  "Content-Type": "application/json",
  "Ocp-Apim-Subscription-Key": "<Ocp-Apim-Subscription-Key>"
}


// const PDF_HELP_BASE_PATH = "https://api.uat.platform.pagopa.it/receipts/helpdesk/v1/"
// const customHeadersPdf_ = {
//   "Content-Type": "application/json",
//   "Ocp-Apim-Subscription-Key": "<Ocp-Apim-Subscription-Key>"
// }


for (const cf of cf_bsimul) {
  let data = {
    "pii": cf
  };

  console.log(`CF>>>>>>>>>>>>>>>>>>>>>>>>>>> ${cf}`);

  const rd = postTokenPii(PDV_TOKENIZER_BASE_PATH, customHeaders_, data);
  rd.then(d => {
    // console.log(d.token);
    console.log(`CF> ${data.pii} token ${d.token}`)

    // retrive Receip5 from CF
    const res = getReceiptsByCf(d.token);
    let p1 = res.then(async function (result) {


      for (const e of result.resources) {
        console.log(`CF>${cf} ${e.eventId} PDFd > ${e.mdAttach != undefined ? e.mdAttach.name : "NA"}`);
        console.log(`CF>${cf} ${e.eventId} PDFp > ${e.mdAttachPayer != undefined ? e.mdAttachPayer.name : "NA"}`);

        // getBiz
        const res0 = getBiz(e.eventId);
        let p0 = res0.then(async function (result0) {
          console.log(result0.resources[0])
        });


        // update Biz to RETRY --- START
        const res2 = updateBizStatusToRETRY(e.eventId);
        let p2 = res2.then(async function (result2) {

          await new Promise(r => setTimeout(r, 10000));
          // console.log(result2);
          // call PDF api ...
          // console.log(`PdfRigenerate for bizId ${e.eventId} sent`);
          const res3 = postPdfRigenerate(PDF_HELP_BASE_PATH, e.eventId, customHeadersPdf_);

          let p3 = res3.then(async function (result3) {
            console.log(`PdfRigenerate ${e.eventId} = ${result3}`);
          });

        });
        // update Biz to RETRY --- STOP

      }

    });
  });

}

