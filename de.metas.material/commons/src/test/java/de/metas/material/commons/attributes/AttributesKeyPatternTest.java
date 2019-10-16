package de.metas.material.commons.attributes;

import static org.assertj.core.api.Assertions.assertThat;

import org.adempiere.mm.attributes.AttributeId;
import org.adempiere.mm.attributes.AttributeValueId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import de.metas.material.event.commons.AttributesKey;

/*
 * #%L
 * metasfresh-material-commons
 * %%
 * Copyright (C) 2019 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class AttributesKeyPatternTest
{
	private static AttributesKeyPattern pattern(final AttributesKeyPartPattern... parts)
	{
		return AttributesKeyPattern.ofParts(ImmutableList.copyOf(parts));
	}

	@Nested
	public static class getSqlLikeString
	{
		@Test
		public void attributeValueId()
		{
			final AttributesKeyPattern pattern = pattern(AttributesKeyPartPattern.ofAttributeValueId(AttributeValueId.ofRepoId(1)));
			assertThat(pattern.getSqlLikeString()).isEqualTo("%1%");
		}

		@Test
		public void attributeIdWildcard()
		{
			final AttributesKeyPattern pattern = pattern(AttributesKeyPartPattern.ofAttributeId(AttributeId.ofRepoId(2)));
			assertThat(pattern.getSqlLikeString()).isEqualTo("%2=%");
		}

		@Test
		public void attributeIdAndValue()
		{
			final AttributesKeyPattern pattern = pattern(AttributesKeyPartPattern.ofStringAttribute(AttributeId.ofRepoId(4), "str"));
			assertThat(pattern.getSqlLikeString()).isEqualTo("%4=str%");
		}

		@Test
		public void mix_attributeValueId_attributeIdWildcard_stringAttribute()
		{
			final AttributesKeyPattern pattern = pattern(
					AttributesKeyPartPattern.ofAttributeValueId(AttributeValueId.ofRepoId(1)),
					AttributesKeyPartPattern.ofAttributeValueId(AttributeValueId.ofRepoId(2)),
					AttributesKeyPartPattern.ofAttributeId(AttributeId.ofRepoId(3)),
					AttributesKeyPartPattern.ofStringAttribute(AttributeId.ofRepoId(4), "str"));

			assertThat(pattern.getSqlLikeString()).isEqualTo("%1%2%3=%4=str%");
		}
	}

	@Nested
	public static class matches
	{
		@Test
		public void all()
		{
			assertThat(AttributesKeyPattern.ALL.matches(AttributesKey.ALL)).isTrue();
			assertThat(AttributesKeyPattern.ALL.matches(AttributesKey.NONE)).isTrue();
			assertThat(AttributesKeyPattern.ALL.matches(AttributesKey.OTHER)).isTrue();
			assertThat(AttributesKeyPattern.ALL.matches(AttributesKey.ofString("111=1"))).isTrue();
		}

		@Test
		public void attributeIdWildcard()
		{
			final AttributesKeyPattern pattern = pattern(AttributesKeyPartPattern.ofAttributeId(AttributeId.ofRepoId(111)));

			assertThat(pattern.matches(AttributesKey.ofString("111=1"))).isTrue();
			assertThat(pattern.matches(AttributesKey.ofString("111=2"))).isTrue();
			assertThat(pattern.matches(AttributesKey.ofString("111="))).isTrue();
			assertThat(pattern.matches(AttributesKey.ofString("222=1"))).isFalse();
		}

		@Test
		public void attributeValueId()
		{
			final AttributesKeyPattern pattern = pattern(AttributesKeyPartPattern.ofAttributeValueId(AttributeValueId.ofRepoId(111)));

			assertThat(pattern.matches(AttributesKey.ofString("111"))).isTrue();
			assertThat(pattern.matches(AttributesKey.ofString("111=1"))).isFalse();
			assertThat(pattern.matches(AttributesKey.ofString("222"))).isFalse();
		}
	}
}
