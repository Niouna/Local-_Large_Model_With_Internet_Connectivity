package cn.edu.wtc.game.repository;

import cn.edu.wtc.game.entity.CharacterTraitDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CharacterTraitDefinitionRepository extends JpaRepository<CharacterTraitDefinition, Long> {

    Optional<CharacterTraitDefinition> findByTraitName(String traitName);

    List<CharacterTraitDefinition> findByTraitNameIn(List<String> traitNames);

    boolean existsByTraitName(String traitName);
}