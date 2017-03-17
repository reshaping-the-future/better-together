/*
 * Copyright (C) 2017 The Better Together Toolkit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package ac.robinson.bettertogether.plugin.base.video.youtube;

public class MessageType {
	public static final int COMMAND_PLAY = 1;
	public static final int COMMAND_PAUSE = 2;
	public static final int COMMAND_SKIP = 3;
	public static final int COMMAND_SEEK = 4;
	public static final int COMMAND_ADD = 5;
	public static final int COMMAND_SELECT = 6;

	public static final int COMMAND_GET_COMMENTS = 7;
	public static final int COMMAND_GET_PLAYLIST = 8;
	public static final int COMMAND_GET_RELATED = 9;
	public static final int COMMAND_GET_SEARCH = 10;

	public static final int COMMAND_GET_STATE = 11;
	public static final int COMMAND_EXIT = 12;

	public static final int INFO_DURATION = 13;

	public static final int JSON_SEARCH = 14;
	public static final int JSON_RELATED = 15;
	public static final int JSON_COMMENTS = 16;
	public static final int JSON_PLAYLIST = 17;
}
