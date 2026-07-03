package com.example.aiinterview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiinterview.entity.CandidateProfileEntity;
import com.example.aiinterview.mapper.CandidateProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class CandidateProfileService {

    @Resource
    private CandidateProfileMapper candidateProfileMapper;

    public CandidateProfileEntity getBySessionId(String sessionId) {
        LambdaQueryWrapper<CandidateProfileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CandidateProfileEntity::getSessionId, sessionId);
        return candidateProfileMapper.selectOne(wrapper);
    }

    @Transactional
    public void saveOrUpdate(String sessionId, String technicalStack, String keyProjects,
                             String strengths, String weaknesses, String careerGoals,
                             String painPoints, String workExperience, String education) {
        CandidateProfileEntity existing = getBySessionId(sessionId);
        if (existing != null) {
            existing.setTechnicalStack(technicalStack);
            existing.setKeyProjects(keyProjects);
            existing.setStrengths(strengths);
            existing.setWeaknesses(weaknesses);
            existing.setCareerGoals(careerGoals);
            existing.setPainPoints(painPoints);
            existing.setWorkExperience(workExperience);
            existing.setEducation(education);
            candidateProfileMapper.updateById(existing);
        } else {
            CandidateProfileEntity entity = new CandidateProfileEntity();
            entity.setSessionId(sessionId);
            entity.setTechnicalStack(technicalStack);
            entity.setKeyProjects(keyProjects);
            entity.setStrengths(strengths);
            entity.setWeaknesses(weaknesses);
            entity.setCareerGoals(careerGoals);
            entity.setPainPoints(painPoints);
            entity.setWorkExperience(workExperience);
            entity.setEducation(education);
            candidateProfileMapper.insert(entity);
        }
    }
}
