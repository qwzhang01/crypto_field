package com.qw.desensitize.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.MybatisMapWrapperFactory;
import com.baomidou.mybatisplus.extension.injector.methods.AlwaysUpdateSomeColumnById;
import com.baomidou.mybatisplus.extension.injector.methods.InsertBatchSomeColumn;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.qw.desensitize.common.encrypt1.DecryptInterceptor;
import com.qw.desensitize.common.encrypt1.EncryptInterceptor;
import com.qw.desensitize.common.encrypt2.EncryptTypeHandler;
import com.qw.desensitize.dto.Encrypt;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;

/**
 * Mybatis Plus Config
 */
@Configuration
@MapperScan("com.qw.desensitize.mapper")
public class MybatisConfig {

    @Bean("mybatisSqlSession")
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, GlobalConfig globalConfig) throws Exception {
        MybatisSqlSessionFactoryBean sqlSessionFactory = new MybatisSqlSessionFactoryBean();
        /* ????????? */
        sqlSessionFactory.setDataSource(dataSource);
        /* xml?????? */
        sqlSessionFactory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:/mapper/*.xml"));
        /* ?????? typeHandler */
        sqlSessionFactory.setTypeHandlersPackage("com.qw.desensitize.config.type");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        /* ?????????????????? */
        configuration.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        mybatisPlusInterceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        sqlSessionFactory.setPlugins(mybatisPlusInterceptor);

        // ???????????????????????????
        configuration.addInterceptor(new EncryptInterceptor());
        configuration.addInterceptor(new DecryptInterceptor());

        // ??????TypeHandlers???????????????????????????????????????
        sqlSessionFactory.setTypeHandlers(new EncryptTypeHandler());
        /* map ?????????????????? */
        configuration.setObjectWrapperFactory(new MybatisMapWrapperFactory());
        sqlSessionFactory.setConfiguration(configuration);
        sqlSessionFactory.setGlobalConfig(globalConfig);
        return sqlSessionFactory.getObject();
    }

    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig conf = new GlobalConfig();
        conf.setDbConfig(new GlobalConfig.DbConfig().setColumnFormat("`%s`"));
        DefaultSqlInjector logicSqlInjector = new DefaultSqlInjector() {
            /**
             * ???????????????????????????
             */
            @Override
            public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
                List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
                // ????????????????????????, ?????????????????????, ????????????????????? UPDATE ?????????
                methodList.add(new InsertBatchSomeColumn(t -> !t.isLogicDelete() && !t.isVersion() && t.getFieldFill() != FieldFill.UPDATE));
                // ????????????????????? INSERT ?????????, ?????????????????? column4 ?????????
                methodList.add(new AlwaysUpdateSomeColumnById(t -> t.getFieldFill() != FieldFill.INSERT && !t.getProperty().equals("column4")));
                return methodList;
            }
        };
        conf.setSqlInjector(logicSqlInjector);
        return conf;
    }

    /**
     * ?????? jackson ???Encrypt??????????????????????????????????????????????????????????????????
     *
     * @return
     */
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // ??????????????????json??????????????????????????????????????????????????????
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Encrypt.class, new JsonSerializer<Encrypt>() {
            @Override
            public void serialize(Encrypt value, JsonGenerator g, SerializerProvider serializers) throws IOException {
                g.writeString(value.getValue());
            }
        });
        simpleModule.addDeserializer(Encrypt.class, new JsonDeserializer<Encrypt>() {
            @Override
            public Encrypt deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                int currentTokenId = p.getCurrentTokenId();
                if (JsonTokenId.ID_STRING == currentTokenId) {
                    String text = p.getText().trim();
                    return new Encrypt(text);
                }
                throw new RuntimeException("json ??????????????????" + Encrypt.class.getSimpleName());
            }
        });
        objectMapper.registerModule(simpleModule);
        return objectMapper;
    }

    /**
     * ??????????????????json??????????????????
     *
     * @return
     */
    @Bean
    public MappingJackson2HttpMessageConverter recruitConverter() {
        return new MappingJackson2HttpMessageConverter(this.objectMapper());
    }
}