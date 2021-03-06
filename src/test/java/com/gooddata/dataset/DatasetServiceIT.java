package com.gooddata.dataset;

import com.gooddata.AbstractGoodDataIT;
import com.gooddata.project.Project;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.util.Collection;

import static net.jadler.Jadler.onRequest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

public class DatasetServiceIT extends AbstractGoodDataIT {

    private Project project;

    @BeforeClass
    public void setUpClass() throws Exception {
        project = MAPPER.readValue(readResource("/project/project.json"), Project.class);
    }

    @BeforeMethod
    public void setUp() throws Exception {
        onRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo("/gdc")
            .respond()
                .withBody(readResource("/gdc/gdc.json"));
        onRequest()
                .havingPath(startsWith("/uploads/"))
                .havingMethodEqualTo("PUT")
            .respond()
                .withStatus(200);
        onRequest()
                .havingPathEqualTo("/gdc/md/PROJECT_ID/etl/pull")
                .havingMethodEqualTo("POST")
            .respond()
                .withStatus(201)
                .withBody(readResource("/dataset/pullTask.json"));
    }

    @Test
    public void shouldLoadDataset() throws Exception {
        onRequest()
                .havingPathEqualTo("/gdc/md/PROJECT/etl/task/ID")
            .respond()
                .withStatus(202)
                .withBody(readResource("/dataset/pullTask.json"))
            .thenRespond()
                .withStatus(200)
                .withBody(readResource("/dataset/pullTaskStatusOk.json"));

        final DatasetManifest manifest = MAPPER.readValue(readResource("/dataset/datasetManifest.json"), DatasetManifest.class);
        gd.getDatasetService().loadDataset(project, manifest, new ByteArrayInputStream(new byte[]{})).get();
    }

    @Test
    public void shouldFailLoading() throws Exception {
        onRequest()
                .havingPathEqualTo("/gdc/md/PROJECT/etl/task/ID")
            .respond()
                .withStatus(200)
                .withBody(readResource("/dataset/pullTaskStatusError.json"));
        final DatasetManifest manifest = MAPPER.readValue(readResource("/dataset/datasetManifest.json"), DatasetManifest.class);
        try {
            gd.getDatasetService().loadDataset(project, manifest, new ByteArrayInputStream(new byte[]{})).get();
            fail("Exception should be thrown");
        } catch (DatasetException e) {
            assertThat(e.getMessage(), is("Load dataset dataset.person failed: status: ERROR"));
        }
    }

    @Test
    public void shouldReadErrorMessages() throws Exception {
        onRequest()
                .havingPathEqualTo("/gdc/md/PROJECT/etl/task/ID")
            .respond()
                .withStatus(200)
                .withBody(readResource("/dataset/pullTaskStatusError.json"));
        onRequest()
                .havingPath(containsString("upload_status.json"))
                .havingMethodEqualTo("GET")
            .respond()
                .withStatus(200)
                .withBody(readResource("/dataset/failStatusComplex.json"));
        onRequest()
                .havingPath(containsString("d_adstpch_querynum.csv.log"))
                .havingMethodEqualTo("GET")
            .respond()
                .withStatus(200)
                .withBody(MAPPER.writeValueAsString(new String[]{"Very error indeed"}));

        final DatasetManifest manifest = MAPPER.readValue(readResource("/dataset/datasetManifest.json"), DatasetManifest.class);
        try {
            gd.getDatasetService().loadDataset(project, manifest, new ByteArrayInputStream(new byte[]{})).get();
            fail("Exception should be thrown");
        } catch (DatasetException e) {
            assertThat(e.getMessage(), is("Load dataset dataset.person failed: [Very error indeed, 1 errors occured during csv import]"));
        }
    }

    @Test
    public void shouldGetDatasetManifest() throws Exception {
        onRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo("/gdc/md/PROJECT_ID/ldm/singleloadinterface/foo/manifest")
            .respond()
                .withBody(readResource("/dataset/datasetManifest.json"))
        ;

        final DatasetManifest manifest = gd.getDatasetService().getDatasetManifest(project, "foo");
        assertThat(manifest, is(notNullValue()));
    }

    @Test
    public void shouldListDatasets() throws Exception {
        onRequest()
                .havingMethodEqualTo("GET")
                .havingPathEqualTo("/gdc/md/PROJECT_ID/ldm/singleloadinterface")
            .respond()
                .withBody(readResource("/dataset/datasets.json"))
        ;

        final Collection<Dataset> datasets = gd.getDatasetService().listDatasets(project);
        assertThat(datasets, hasSize(1));
    }
}