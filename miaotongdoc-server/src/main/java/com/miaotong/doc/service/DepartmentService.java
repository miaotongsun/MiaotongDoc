package com.miaotong.doc.service;

import com.miaotong.doc.entity.Department;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional
    public Department create(String code, String name, Long parentId, Integer sortOrder) {
        if (departmentRepository.findByCode(code).isPresent()) {
            throw new BusinessException("部门编码已存在");
        }

        Department dept = new Department();
        dept.setCode(code);
        dept.setName(name);
        dept.setSortOrder(sortOrder != null ? sortOrder : 0);

        if (parentId != null) {
            Department parent = departmentRepository.findById(parentId)
                    .orElseThrow(() -> new NotFoundException("上级部门不存在"));
            dept.setParentId(parentId);
            dept.setLevel((short) (parent.getLevel() + 1));
            dept.setPath(parent.getPath() + "/" + code);
        } else {
            dept.setParentId(null);
            dept.setLevel((short) 1);
            dept.setPath("/" + code);
        }

        return departmentRepository.save(dept);
    }

    @Transactional
    public Department update(Long id, String name, String code, Integer sortOrder) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("部门不存在"));
        if (name != null) dept.setName(name);
        if (code != null) dept.setCode(code);
        if (sortOrder != null) dept.setSortOrder(sortOrder);
        return departmentRepository.save(dept);
    }

    @Transactional
    public void deactivate(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("部门不存在"));
        dept.setIsActive(false);
        departmentRepository.save(dept);
    }
}
