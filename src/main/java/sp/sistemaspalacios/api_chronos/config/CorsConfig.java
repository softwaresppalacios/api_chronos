package sp.sistemaspalacios.api_chronos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.addAllowedOrigin("https://192.168.23.5:4200");
        corsConfiguration.addAllowedOrigin("http://192.168.23.5:4200");
        corsConfiguration.addAllowedOrigin("http://localhost:4200");
        corsConfiguration.addAllowedOrigin("http://192.168.23.3:4200");
        corsConfiguration.addAllowedOrigin("http://192.168.23.7:4200");
        corsConfiguration.addAllowedMethod("*");
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);

        return new CorsFilter(source);
    }
}
