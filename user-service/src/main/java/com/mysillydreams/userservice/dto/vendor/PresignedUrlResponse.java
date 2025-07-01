package com.mysillydreams.userservice.dto.vendor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing a pre-signed URL for S3 object upload and the corresponding object key.")
public class PresignedUrlResponse {

    @Schema(description = "The pre-signed URL that can be used to upload an object directly to S3.",
            example = "https://s3.amazonaws.com/bucket/object-key?AWSAccessKeyId=...")
    private String url;

    @Schema(description = "The S3 object key for the uploaded file. This key should be used in any subsequent callback or processing.",
            example = "vendor-docs/profile-uuid/doc-type/random-uuid")
    private String key;
}
