package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysForm;
import com.smart.admin.entity.SysFormData;
import com.smart.admin.mapper.SysFormDataMapper;
import com.smart.admin.mapper.SysFormMapper;
import com.smart.admin.service.SysFormService;
import com.smart.common.core.enums.StatusFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Form service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFormServiceImpl extends ServiceImpl<SysFormMapper, SysForm>
        implements SysFormService {

    private final SysFormDataMapper formDataMapper;

    @Override
    @Transactional
    public void publishForm(Long formId) {
        SysForm form = getById(formId);
        if (form == null) {
            throw new IllegalArgumentException("Form not found");
        }

        form.setStatus(StatusFlag.ENABLED.getValue());
        updateById(form);
        log.debug("Form published: {}", formId);
    }

    @Override
    public SysForm getFormByKey(String formKey) {
        return getOne(new LambdaQueryWrapper<SysForm>()
                .eq(SysForm::getFormKey, formKey)
                .eq(SysForm::getStatus, StatusFlag.ENABLED.getValue()));
    }

    @Override
    @Transactional
    public void submitFormData(Long formId, String formKey, Long userId,
                               String formData, String ip, String userAgent) {
        SysFormData data = new SysFormData();
        data.setFormId(formId);
        data.setFormKey(formKey);
        data.setUserId(userId);
        data.setFormData(formData);
        data.setIp(ip);
        data.setUserAgent(userAgent);
        data.setStatus(StatusFlag.ENABLED.getValue());

        formDataMapper.insert(data);
        log.info("Form data submitted: formId={}, userId={}", formId, userId);
    }

    @Override
    public List<SysFormData> getFormDataList(Long formId) {
        return formDataMapper.selectList(new LambdaQueryWrapper<SysFormData>()
                .eq(SysFormData::getFormId, formId)
                .orderByDesc(SysFormData::getCreateTime));
    }

    @Override
    public List<SysFormData> getUserFormData(Long formId, Long userId) {
        return formDataMapper.selectList(new LambdaQueryWrapper<SysFormData>()
                .eq(SysFormData::getFormId, formId)
                .eq(SysFormData::getUserId, userId)
                .orderByDesc(SysFormData::getCreateTime));
    }
}