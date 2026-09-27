package com.glint.server.web.metro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glint.server.biz.metro.GaodeMetroClient;
import com.glint.server.biz.metro.GaodeProperties;
import com.glint.server.common.metro.MetroContracts.MetroCity;
import com.glint.server.common.metro.MetroContracts.MetroCityNetwork;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GaodeMetroClientParsingTest {

    @Test
    void parsesBeijingAndShanghaiSamples() throws Exception {
        GaodeProperties properties = new GaodeProperties();
        GaodeMetroClient client = new GaodeMetroClient(properties, new ObjectMapper()) {
            @Override
            protected String fetchRaw(String srhdata) throws Exception {
                String resource = switch (srhdata) {
                    case "citylist.json" -> "/metro/citylist-sample.json";
                    case "1100_drw_beijing.json" -> "/metro/beijing-sample.json";
                    case "3100_drw_shanghai.json" -> "/metro/shanghai-sample.json";
                    default -> throw new IllegalArgumentException(srhdata);
                };
                try (InputStream stream = GaodeMetroClientParsingTest.class.getResourceAsStream(resource)) {
                    assertThat(stream).isNotNull();
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        };

        assertThat(client.fetchCities()).extracting(MetroCity::cityCode).containsExactly("1100", "3100");
        MetroCityNetwork beijing = client.fetchCityNetwork(new MetroCity("1100", "北京市", "beijing"));
        MetroCityNetwork shanghai = client.fetchCityNetwork(new MetroCity("3100", "上海市", "shanghai"));
        assertThat(beijing.lines()).hasSize(2);
        assertThat(beijing.stations()).hasSize(3);
        assertThat(shanghai.lines()).hasSize(1);
        assertThat(shanghai.stations().get(0).stationName()).isEqualTo("莘庄");
        assertThat(shanghai.stations().get(0).longitude()).isEqualTo(121.385373);
    }
}
