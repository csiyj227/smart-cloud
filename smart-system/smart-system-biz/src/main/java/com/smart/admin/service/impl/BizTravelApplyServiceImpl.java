package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.BizTravelApply;
import com.smart.admin.mapper.BizTravelApplyMapper;
import com.smart.admin.service.BizTravelApplyService;
import org.springframework.stereotype.Service;

@Service
public class BizTravelApplyServiceImpl extends ServiceImpl<BizTravelApplyMapper, BizTravelApply>
        implements BizTravelApplyService {
}
