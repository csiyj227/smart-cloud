package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysForm;
import com.smart.admin.entity.SysFormData;

import java.util.List;
import java.util.Map;

/**
 * Form service interface.
 */
public interface SysFormService extends IService<SysForm> {

    /**
     * Publish a form.
     */
    void publishForm(Long formId);

    /**
     * Get form schema by form key.
     */
    SysForm getFormByKey(String formKey);

    /**
     * Submit form data.
     */
    void submitFormData(Long formId, String formKey, Long userId, String formData, String ip, String userAgent);

    /**
     * Get form data by form ID.
     */
    List<SysFormData> getFormDataList(Long formId);

    /**
     * Get user's submitted form data.
     */
    List<SysFormData> getUserFormData(Long formId, Long userId);
}