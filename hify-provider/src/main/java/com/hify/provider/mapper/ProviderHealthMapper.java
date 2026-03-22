package com.hify.provider.mapper;

import com.hify.common.mapper.BaseMapper;
import com.hify.provider.entity.ProviderHealth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface ProviderHealthMapper extends BaseMapper<ProviderHealth> {

    @Select("SELECT * FROM provider_health WHERE provider_id = #{providerId}")
    Optional<ProviderHealth> findByProviderId(Long providerId);
}
