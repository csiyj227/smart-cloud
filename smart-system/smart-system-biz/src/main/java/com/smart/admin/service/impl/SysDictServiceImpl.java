package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysDict;
import com.smart.admin.entity.SysDictItem;
import com.smart.admin.mapper.SysDictItemMapper;
import com.smart.admin.mapper.SysDictMapper;
import com.smart.admin.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysDictServiceImpl extends ServiceImpl<SysDictMapper, SysDict> implements SysDictService {

    private final SysDictItemMapper dictItemMapper;

    @Override
    public SysDict findByTypeCode(String typeCode, Long tenantId) {
        return getOne(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getTypeCode, typeCode)
                .eq(SysDict::getTenantId, tenantId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictWithItems(Long id) {
        // P1-09: Cascade delete dict items before deleting the dict
        dictItemMapper.delete(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictId, id));
        removeById(id);
    }
}