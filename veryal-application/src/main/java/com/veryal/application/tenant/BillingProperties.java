package com.veryal.application.tenant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 账单单价（分）。0 表示不计费/仅统计用量。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.billing")
public class BillingProperties {

    private long userCents;

    private long groupCents;

    private long storageGbCents;
}
