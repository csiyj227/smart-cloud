package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysDictItem;

import java.util.List;

public interface SysDictItemService extends IService<SysDictItem> {

    List<SysDictItem> findByDictId(Long dictId);
}