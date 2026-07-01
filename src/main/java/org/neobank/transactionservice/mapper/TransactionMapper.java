package org.neobank.transactionservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.neobank.transactionservice.dto.TranResp;
import org.neobank.transactionservice.dto.TransactionResponse;
import org.neobank.transactionservice.entity.Transaction;

@Mapper(componentModel = "spring")
public abstract class TransactionMapper {

    @Mapping(target = "type", expression = "java(mapType(transaction, currentUserId))")
    public abstract TransactionResponse toResponse(Transaction transaction, String currentUserId);

    @Mapping(target = "type", expression = "java(mapType(transaction, currentUserId))")
    public abstract TranResp tranResp(Transaction transaction, String currentUserId);

    protected String mapType(Transaction transaction, String currentUserId) {
        if (transaction.getType() != org.neobank.transactionservice.enums.TransactionType.TRANSFER) {
            return transaction.getType().name();
        }
        
        boolean isSender = currentUserId.equals(transaction.getKeycloakUserId());
        if (isSender) {
            return "TRANSFER_OUT";
        } else {
            return "TRANSFER_IN";
        }
    }
}
