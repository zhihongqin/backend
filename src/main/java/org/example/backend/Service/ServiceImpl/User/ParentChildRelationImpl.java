package org.example.backend.Service.ServiceImpl.User;

import java.util.List;
import org.example.backend.Entity.User.ParentChildRelation;
import org.example.backend.Mapper.User.ParentChildRelationMapper;
import org.example.backend.Service.User.ParentChildRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ParentChildRelationImpl implements ParentChildRelationService {
  @Autowired
  private ParentChildRelationMapper parentChildRelationMapper;

  @Override
  public List<ParentChildRelation> getAllRelations() {
    return parentChildRelationMapper.selectList(null);
  }

  @Override
  public ParentChildRelation getRelationById(int id) {
    return parentChildRelationMapper.selectById(id);
  }

  @Override
  public int createRelation(ParentChildRelation relation) {
    return parentChildRelationMapper.insert(relation);
  }

  @Override
  public int updateRelation(ParentChildRelation relation) {
    return parentChildRelationMapper.updateById(relation);
  }

  @Override
  public int deleteRelationById(int id) {
    return parentChildRelationMapper.deleteById(id);
  }
}
