
const getPii = async function getPdvPii(url, token, customHeaders,data) {
    return fetch(url+`tokens/${token}/pii`, {
        method: "GET",
        headers: customHeaders,
        // body: JSON.stringify(data),
        })
        .then((response) => response.json())
        .then((data) => {
            // console.log(data);
            return data;
        }).catch((error) => {
            console.error('error in execution', error);
        });
}

const postTokenPii = async function postTokenPii(url,customHeaders,data) {
    return fetch(url+`tokens/search`, {
        method: "POST",
        headers: customHeaders,
        body: JSON.stringify(data),
        })
        .then((response) => response.json())
        .then((data) => {
            // console.log(data);
            return data;
        }).catch((error) => {
            console.error('error in execution', error);
        });
}


const postPdfRigenerate = async function postPdfRigenerate(url, eventid, customHeaders,data) {
    return fetch(url+`/receipts/${eventid}/regenerate-receipt-pdf`, {
        method: "POST",
        headers: customHeaders,
        body: JSON.stringify(data),
        })
        .then((response) => response.status)
        .then((data) => {
            // console.log(data);
            return data;
        }).catch((error) => {
            console.error('error in execution', error);
        });
}


module.exports = {
    getPii, postTokenPii, postPdfRigenerate
}
