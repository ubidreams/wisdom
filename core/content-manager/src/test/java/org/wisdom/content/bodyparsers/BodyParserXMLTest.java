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
package org.wisdom.content.bodyparsers;

import org.junit.Before;
import org.junit.Test;
import org.wisdom.api.http.MimeTypes;
import org.wisdom.content.jackson.JacksonSingleton;
import org.wisdom.test.parents.FakeContext;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check the XML parsing.
 */
public class BodyParserXMLTest {
    private BodyParserXML parser;

    /**
     * This field is used to retrieve the Type.
     */
    List<Data> tmp;

    @Before
    public void setUp() {
        JacksonSingleton singleton = new JacksonSingleton();
        singleton.validate();
        parser = new BodyParserXML();
        parser.xml = singleton;
    }

    @Test
    public void testParsing() {
        FakeContext context = new FakeContext().setBody(
                "<Data>" +
                        "<name>wisdom</name>" +
                        "<age>2</age>" +
                        "<friends>" +
                            "<friend>clement</friend>" +
                            "<friend>jonathan</friend>" +
                        "</friends>" +
                "</Data>");
        Data data = parser.invoke(context, Data.class);
        assertThat(data.getName()).isEqualTo("wisdom");
        assertThat(data.getFriends()).containsExactly("clement", "jonathan");
        assertThat(data.getAge()).isEqualTo(2);
    }

    @Test
    public void testParsingWithGenericType() throws NoSuchFieldException {
        Type type = this.getClass().getDeclaredField("tmp").getGenericType();
        FakeContext context = new FakeContext().setBody( "<List><Data>" +
                "<name>wisdom</name>" +
                "<age>2</age>" +
                "<friends>" +
                "<friend>clement</friend>" +
                "<friend>jonathan</friend>" +
                "</friends>" +
                "</Data></List>");
        List<Data> list =  parser.invoke(context, List.class, type);
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).isInstanceOf(Data.class);
        Data data = list.get(0);
        assertThat(data.getName()).isEqualTo("wisdom");
        assertThat(data.getFriends()).containsExactly("clement", "jonathan");
        assertThat(data.getAge()).isEqualTo(2);
    }

    @Test
    public void testMimeTypes() {
        assertThat(parser.getContentTypes())
                .hasSize(3).contains(MimeTypes.XML);
    }

    @Test
    public void testParsingError() {
        FakeContext context = new FakeContext().setBody(
                "<Data>" +
                        "<name>wisdom</name>" +
                        "<age>2</age>" +
                        "<friends>" +
                        "<friend>clement</friend>" +
                        "<friend>jonathan</friend>" +
                        "<friends>" + // On purpose
                        "</Data>");
        assertThat(parser.invoke(context, Data.class)).isNull();
    }

}