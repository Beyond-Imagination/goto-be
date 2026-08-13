package kr.bi.go_to.enums;

public enum AgreementType {
    AGE_CONFIRMATION(0, true),
    TERMS_OF_SERVICE(1, true),
    PERSONAL_INFORMATION_COLLECTION_AND_USE(2, true),
    LOCATION_BASED_SERVICE(3, true),
    MARKETING_INFORMATION_RECEIPT(4, false);

    public static final long REQUIRED_MASK = (1L << 4) - 1;

    private final long mask;
    private final boolean required;

    AgreementType(int bit, boolean required) {
        this.mask = 1L << bit;
        this.required = required;
    }

    public long getMask() {
        return mask;
    }

    public boolean isRequired() {
        return required;
    }

    public static boolean hasRequiredAgreements(long agreementMask) {
        return (agreementMask & REQUIRED_MASK) == REQUIRED_MASK;
    }
}
