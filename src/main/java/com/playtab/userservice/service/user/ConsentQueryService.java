package com.playtab.userservice.service.user;

import com.playtab.userservice.entity.AuthConsent;
import com.playtab.userservice.entity.enums.ConsentType;
import com.playtab.userservice.repository.AuthConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsentQueryService {

    private final AuthConsentRepository consentRepo;

    public MarketingConsentSummary getLatestMarketing(UUID identityId) {
        var page = consentRepo.findLatestByType(identityId, ConsentType.MARKETING, PageRequest.of(0, 1));
        if (page.isEmpty()) {
            return new MarketingConsentSummary(false, null);
        }
        AuthConsent c = page.getContent().get(0);
        return new MarketingConsentSummary(Boolean.TRUE.equals(c.getIsAgreed()), c.getTermsVersion());
    }

    public record MarketingConsentSummary(boolean agreed, String termsVersion) {}
}
