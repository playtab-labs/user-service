package com.playtab.userservice.service.user;

import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.exception.DomainException;
import com.playtab.userservice.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class ConsentPolicy {

    public void validateRequiredConsents(Collection<ConsentInputLike> inputs) {
        boolean serviceAgreed = inputs.stream()
                .anyMatch(c -> c.type() == ConsentType.SERVICE && c.isAgreed());
        boolean privacyAgreed = inputs.stream()
                .anyMatch(c -> c.type() == ConsentType.PRIVACY && c.isAgreed());

        if (!serviceAgreed || !privacyAgreed) {
            throw new DomainException(ErrorCode.REQUIRED_CONSENT_MISSING);
        }
    }

    /** proto/entity 의존 줄이려고 최소 인터페이스 */
    public interface ConsentInputLike {
        ConsentType type();
        boolean isAgreed();
    }
}
