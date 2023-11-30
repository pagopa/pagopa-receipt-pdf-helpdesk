package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration;

public enum ReasonErrorCode {
    ERROR_QUEUE(902),
    ERROR_COSMOS(904),
    ERROR_PDV_IO(800),
    ERROR_PDV_UNEXPECTED(801),
    ERROR_PDV_MAPPING(802),
    ERROR_BLOB_STORAGE(901),
    ERROR_PDF_ENGINE(700),
    ERROR_TEMPLATE_PDF(903);


    private final int code;

    ReasonErrorCode(int code){
        this.code = code;
    }

    public int getCode(){
        return this.code;
    }
}
