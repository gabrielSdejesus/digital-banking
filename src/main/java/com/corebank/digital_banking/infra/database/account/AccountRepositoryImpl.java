package com.corebank.digital_banking.infra.database.account;

import com.corebank.digital_banking.domain.account.Account;
import com.corebank.digital_banking.domain.account.AccountRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AccountRepositoryImpl implements AccountRepository {

    private final SpringDataAccountRepository springDataAccountRepository;

    public AccountRepositoryImpl(SpringDataAccountRepository springDataAccountRepository) {
        this.springDataAccountRepository = springDataAccountRepository;
    }

    @Override
    public Account save(Account account) {
        AccountDbEntity dbEntity = new AccountDbEntity(
                account.getId().toString(),
                account.getHolderName(),
                account.getBalance(),
                account.getCreatedAt(),
                true
        );

        AccountDbEntity savedEntity = springDataAccountRepository.save(dbEntity);

        return new Account(
                UUID.fromString(savedEntity.getId()),
                savedEntity.getHolderName(),
                savedEntity.getBalance(),
                savedEntity.getCreatedAt()
        );
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return springDataAccountRepository.findById(id.toString())
                .map(entity -> new Account(
                        UUID.fromString(entity.getId()),
                        entity.getHolderName(),
                        entity.getBalance(),
                        entity.getCreatedAt()
                ));
    }
}
