/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2015 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wisdom.framework.instances.it;

import org.apache.felix.ipojo.Factory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.ow2.chameleon.testing.helpers.IPOJOHelper;
import org.ow2.chameleon.testing.helpers.OSGiHelper;
import org.wisdom.framework.instances.component.AlreadyInstantiatedInstance;
import org.wisdom.framework.instances.component.MyComponent;
import org.wisdom.test.parents.WisdomTest;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;


public class InstanceCreatedAtBootIT extends WisdomTest {

    @Inject
    ConfigurationAdmin admin;

    @Inject
    BundleContext context;
    private OSGiHelper osgi;
    private IPOJOHelper ipojo;

    @Before
    public void init() throws IOException, InvalidSyntaxException {
        osgi = new OSGiHelper(context);
        ipojo = new IPOJOHelper(context);
    }

    @After
    public void shutdown() throws IOException, InvalidSyntaxException {
        osgi.dispose();
        ipojo.dispose();
    }


    @Test
    public void testThatInstanceUsingCfgAreCreated() {
        final Factory factory1 = ipojo.getFactory("org.wisdom.framework.instances.component.AlreadyInstantiatedInstance");
        final Factory factory2 = ipojo.getFactory("org.wisdom.framework.instances.component" +
                ".AlreadyInstantiatedInstanceFromFactory");

        assertThat(factory1).isNotNull();
        assertThat(factory2).isNotNull();

        await().atMost(1, TimeUnit.MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return ! factory1.getInstances().isEmpty();
            }
        });

        await().atMost(1, TimeUnit.MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return ! factory2.getInstances().isEmpty();
            }
        });

        assertThat(factory1.getInstances()).hasSize(1);
        assertThat(factory2.getInstances()).hasSize(2);

    }

}
