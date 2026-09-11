package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.response.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService {

    /*
     * Stores the file under a fresh object key and records it in image_files.
     * Every call creates a new row (never an overwrite), so an image a published version points at
     * is never replaced; images nobody ends up referencing are left in the bucket by design -
     * a sweeper job carries exactly the worst risk this system has, deleting the photo on a
     * version already sent to a customer.
     */
    ImageUploadResponse upload(MultipartFile file);
}
