package it.gov.pagopa.receipt.pdf.helpdesk.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IONotifyErrorRecoveryRequest {

    private String eventId;
    private boolean generatedStatus;
    private boolean ioErrorToNotifyStatus;

}