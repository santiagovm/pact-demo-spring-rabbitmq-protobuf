package com.vasquez;

public class BeerVerificationServiceMock implements BeerVerificationService {

    @Override
    public void process(BeerVerification verification) {
        _verification = verification;
    }

    public BeerVerification getVerificationInRequest() {
        return _verification;
    }

    private BeerVerification _verification;

    public void reset() {
        _verification = null;
    }
}
