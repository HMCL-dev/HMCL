/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

/**
 *
 */
public enum SVG {
    ADD("M19,M11 13H5V11H11V5H13V11H19V13H13V19H11V13Z"),
    ADD_CIRCLE("M11 17H13V13H17V11H13V7H11V11H7V13H11V17ZM12 22Q9.925 22 8.1 21.2125T4.925 19.075Q3.575 17.725 2.7875 15.9T2 12Q2 9.925 2.7875 8.1T4.925 4.925Q6.275 3.575 8.1 2.7875T12 2Q14.075 2 15.9 2.7875T19.075 4.925Q20.425 6.275 21.2125 8.1T22 12Q22 14.075 21.2125 15.9T19.075 19.075Q17.725 20.425 15.9 21.2125T12 22ZM12 20Q15.35 20 17.675 17.675T20 12Q20 8.65 17.675 6.325T12 4Q8.65 4 6.325 6.325T4 12Q4 15.35 6.325 17.675T12 20ZM12 12Z"),
    ALPHA_CIRCLE("M11,7H13A2,2 0 0,1 15,9V17H13V13H11V17H9V9A2,2 0 0,1 11,7M11,9V11H13V9H11M12,20A8,8 0 0,0 20,12A8,8 0 0,0 12,4A8,8 0 0,0 4,12A8,8 0 0,0 12,20M12,2A10,10 0 0,1 22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 12,2Z"), // TODO
    APPLICATION("M21 2H3C1.9 2 1 2.9 1 4V20C1 21.1 1.9 22 3 22H21C22.1 22 23 21.1 23 20V4C23 2.9 22.1 2 21 2M21 20H3V6H21V20Z"), // TODO
    ARROW_BACK("M7.825 13 13.425 18.6 12 20 4 12 12 4 13.425 5.4 7.825 11H20V13H7.825Z"),
    ARROW_DROP_DOWN("M12 15 7 10H17L12 15Z"),
    ARROW_DROP_UP("M7 14 12 9 17 14H7Z"),
    ARROW_FORWARD("M16.175 13H4V11H16.175L10.575 5.4 12 4 20 12 12 20 10.575 18.6 16.175 13Z"),
    BETA_CIRCLE("M15,10.5C15,11.3 14.3,12 13.5,12C14.3,12 15,12.7 15,13.5V15A2,2 0 0,1 13,17H9V7H13A2,2 0 0,1 15,9V10.5M13,15V13H11V15H13M13,11V9H11V11H13M12,2A10,10 0 0,1 22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 12,2M12,4A8,8 0 0,0 4,12A8,8 0 0,0 12,20A8,8 0 0,0 20,12A8,8 0 0,0 12,4Z"), // TODO
    CANCEL("M8.4 17 12 13.4 15.6 17 17 15.6 13.4 12 17 8.4 15.6 7 12 10.6 8.4 7 7 8.4 10.6 12 7 15.6 8.4 17ZM12 22Q9.925 22 8.1 21.2125T4.925 19.075Q3.575 17.725 2.7875 15.9T2 12Q2 9.925 2.7875 8.1T4.925 4.925Q6.275 3.575 8.1 2.7875T12 2Q14.075 2 15.9 2.7875T19.075 4.925Q20.425 6.275 21.2125 8.1T22 12Q22 14.075 21.2125 15.9T19.075 19.075Q17.725 20.425 15.9 21.2125T12 22ZM12 20Q15.35 20 17.675 17.675T20 12Q20 8.65 17.675 6.325T12 4Q8.65 4 6.325 6.325T4 12Q4 15.35 6.325 17.675T12 20ZM12 12Z"),
    CHAT("M6 14H14V12H6V14ZM6 11H18V9H6V11ZM6 8H18V6H6V8ZM2 22V4Q2 3.175 2.5875 2.5875T4 2H20Q20.825 2 21.4125 2.5875T22 4V16Q22 16.825 21.4125 17.4125T20 18H6L2 22ZM5.15 16H20V4H4V17.125L5.15 16ZM4 16V4 16Z"),
    CHECK("M9.55 18 3.85 12.3 5.275 10.875 9.55 15.15 18.725 5.975 20.15 7.4 9.55 18Z"),
    CHECKROOM("M3 20Q2.575 20 2.2875 19.7125T2 19Q2 18.75 2.1 18.5375T2.4 18.2L11 11.75V10Q11 9.575 11.3 9.2875T12.025 9Q12.65 9 13.075 8.55T13.5 7.475Q13.5 6.85 13.0625 6.425T12 6Q11.375 6 10.9375 6.4375T10.5 7.5H8.5Q8.5 6.05 9.525 5.025T12 4Q13.45 4 14.475 5.0125T15.5 7.475Q15.5 8.65 14.8125 9.575T13 10.85V11.75L21.6 18.2Q21.8 18.325 21.9 18.5375T22 19Q22 19.425 21.7125 19.7125T21 20H3ZM6 18H18L12 13.5 6 18Z"),
    CHECK_CIRCLE("M10.6 16.6 17.65 9.55 16.25 8.15 10.6 13.8 7.75 10.95 6.35 12.35 10.6 16.6ZM12 22Q9.925 22 8.1 21.2125T4.925 19.075Q3.575 17.725 2.7875 15.9T2 12Q2 9.925 2.7875 8.1T4.925 4.925Q6.275 3.575 8.1 2.7875T12 2Q14.075 2 15.9 2.7875T19.075 4.925Q20.425 6.275 21.2125 8.1T22 12Q22 14.075 21.2125 15.9T19.075 19.075Q17.725 20.425 15.9 21.2125T12 22ZM12 20Q15.35 20 17.675 17.675T20 12Q20 8.65 17.675 6.325T12 4Q8.65 4 6.325 6.325T4 12Q4 15.35 6.325 17.675T12 20ZM12 12Z"),
    CLOSE("M6.4 19 5 17.6 10.6 12 5 6.4 6.4 5 12 10.6 17.6 5 19 6.4 13.4 12 19 17.6 17.6 19 12 13.4 6.4 19Z"),
    CONTENT_COPY("M9 18Q8.175 18 7.5875 17.4125T7 16V4Q7 3.175 7.5875 2.5875T9 2H18Q18.825 2 19.4125 2.5875T20 4V16Q20 16.825 19.4125 17.4125T18 18H9ZM9 16H18V4H9V16ZM5 22Q4.175 22 3.5875 21.4125T3 20V6H5V20H16V22H5ZM9 16V4 16Z"),
    DELETE("M7 21Q6.175 21 5.5875 20.4125T5 19V6H4V4H9V3H15V4H20V6H19V19Q19 19.825 18.4125 20.4125T17 21H7ZM17 6H7V19H17V6ZM9 17H11V8H9V17ZM13 17H15V8H13V17ZM7 6V19 6Z"),
    DEPLOYED_CODE("M11 19.425V12.575L5 9.1V15.95L11 19.425ZM13 19.425 19 15.95V9.1L13 12.575V19.425ZM12 10.85 17.925 7.425 12 4 6.075 7.425 12 10.85ZM4 17.7Q3.525 17.425 3.2625 16.975T3 15.975V8.025Q3 7.475 3.2625 7.025T4 6.3L11 2.275Q11.475 2 12 2T13 2.275L20 6.3Q20.475 6.575 20.7375 7.025T21 8.025V15.975Q21 16.525 20.7375 16.975T20 17.7L13 21.725Q12.525 22 12 22T11 21.725L4 17.7ZM12 12Z"),
    DOWNLOAD("M12 16 7 11 8.4 9.55 11 12.15V4H13V12.15L15.6 9.55 17 11 12 16ZM6 20Q5.175 20 4.5875 19.4125T4 18V15H6V18H18V15H20V18Q20 18.825 19.4125 19.4125T18 20H6Z"),
    EDIT("M5 19H6.425L16.2 9.225 14.775 7.8 5 17.575V19ZM3 21V16.75L16.2 3.575Q16.5 3.3 16.8625 3.15T17.625 3Q18.025 3 18.4 3.15T19.05 3.6L20.425 5Q20.725 5.275 20.8625 5.65T21 6.4Q21 6.8 20.8625 7.1625T20.425 7.825L7.25 21H3ZM19 6.4 17.6 5 19 6.4ZM15.475 8.525 14.775 7.8 16.2 9.225 15.475 8.525Z"),
    ERROR("M12 17Q12.425 17 12.7125 16.7125T13 16Q13 15.575 12.7125 15.2875T12 15Q11.575 15 11.2875 15.2875T11 16Q11 16.425 11.2875 16.7125T12 17ZM11 13H13V7H11V13ZM12 22Q9.925 22 8.1 21.2125T4.925 19.075Q3.575 17.725 2.7875 15.9T2 12Q2 9.925 2.7875 8.1T4.925 4.925Q6.275 3.575 8.1 2.7875T12 2Q14.075 2 15.9 2.7875T19.075 4.925Q20.425 6.275 21.2125 8.1T22 12Q22 14.075 21.2125 15.9T19.075 19.075Q17.725 20.425 15.9 21.2125T12 22ZM12 20Q15.35 20 17.675 17.675T20 12Q20 8.65 17.675 6.325T12 4Q8.65 4 6.325 6.325T4 12Q4 15.35 6.325 17.675T12 20ZM12 12Z"),
    EXPORT("M23,12L19,8V11H10V13H19V16M1,18V6C1,4.89 1.9,4 3,4H15A2,2 0 0,1 17,6V9H15V6H3V18H15V15H17V18A2,2 0 0,1 15,20H3A2,2 0 0,1 1,18Z"), // TODO
    EXTENSION("M8.8 21H5Q4.175 21 3.5875 20.4125T3 19V15.2Q4.2 15.2 5.1 14.4375T6 12.5Q6 11.325 5.1 10.5625T3 9.8V6Q3 5.175 3.5875 4.5875T5 4H9Q9 2.95 9.725 2.225T11.5 1.5Q12.55 1.5 13.275 2.225T14 4H18Q18.825 4 19.4125 4.5875T20 6V10Q21.05 10 21.775 10.725T22.5 12.5Q22.5 13.55 21.775 14.275T20 15V19Q20 19.825 19.4125 20.4125T18 21H14.2Q14.2 19.75 13.4125 18.875T11.5 18Q10.375 18 9.5875 18.875T8.8 21ZM5 19H7.125Q7.725 17.35 9.05 16.675T11.5 16Q12.625 16 13.95 16.675T15.875 19H18V13H20Q20.2 13 20.35 12.85T20.5 12.5Q20.5 12.3 20.35 12.15T20 12H18V6H12V4Q12 3.8 11.85 3.65T11.5 3.5Q11.3 3.5 11.15 3.65T11 4V6H5V8.2Q6.35 8.7 7.175 9.875T8 12.5Q8 13.925 7.175 15.1T5 16.8V19ZM11.5 12.5Z"),
    FEEDBACK("M12 15Q12.425 15 12.7125 14.7125T13 14Q13 13.575 12.7125 13.2875T12 13Q11.575 13 11.2875 13.2875T11 14Q11 14.425 11.2875 14.7125T12 15ZM11 11H13V5H11V11ZM2 22V4Q2 3.175 2.5875 2.5875T4 2H20Q20.825 2 21.4125 2.5875T22 4V16Q22 16.825 21.4125 17.4125T20 18H6L2 22ZM5.15 16H20V4H4V17.125L5.15 16ZM4 16V4 16Z"),
    FOLDER("M4 20Q3.175 20 2.5875 19.4125T2 18V6Q2 5.175 2.5875 4.5875T4 4H10L12 6H20Q20.825 6 21.4125 6.5875T22 8V18Q22 18.825 21.4125 19.4125T20 20H4ZM4 18H20V8H11.175L9.175 6H4V18ZM4 18V6 18Z"),
    FOLDER_OPEN("M4 20Q3.175 20 2.5875 19.4125T2 18V6Q2 5.175 2.5875 4.5875T4 4H10L12 6H20Q20.825 6 21.4125 6.5875T22 8H11.175L9.175 6H4V18L6.4 10H23.5L20.925 18.575Q20.725 19.225 20.1875 19.6125T19 20H4ZM6.1 18H19L20.8 12H7.9L6.1 18ZM6.1 18 7.9 12 6.1 18ZM4 8V6 8Z"),
    GAMEPAD("M6,9H8V11H10V13H8V15H6V13H4V11H6V9M18.5,9A1.5,1.5 0 0,1 20,10.5A1.5,1.5 0 0,1 18.5,12A1.5,1.5 0 0,1 17,10.5A1.5,1.5 0 0,1 18.5,9M15.5,12A1.5,1.5 0 0,1 17,13.5A1.5,1.5 0 0,1 15.5,15A1.5,1.5 0 0,1 14,13.5A1.5,1.5 0 0,1 15.5,12M17,5A7,7 0 0,1 24,12A7,7 0 0,1 17,19C15.04,19 13.27,18.2 12,16.9C10.73,18.2 8.96,19 7,19A7,7 0 0,1 0,12A7,7 0 0,1 7,5H17M7,7A5,5 0 0,0 2,12A5,5 0 0,0 7,17C8.64,17 10.09,16.21 11,15H13C13.91,16.21 15.36,17 17,17A5,5 0 0,0 22,12A5,5 0 0,0 17,7H7Z"), // TODO
    HELP("M11.95 18Q12.475 18 12.8375 17.6375T13.2 16.75Q13.2 16.225 12.8375 15.8625T11.95 15.5Q11.425 15.5 11.0625 15.8625T10.7 16.75Q10.7 17.275 11.0625 17.6375T11.95 18ZM11.05 14.15H12.9Q12.9 13.325 13.0875 12.85T14.15 11.55Q14.8 10.9 15.175 10.3125T15.55 8.9Q15.55 7.5 14.525 6.75T12.1 6Q10.675 6 9.7875 6.75T8.55 8.55L10.2 9.2Q10.325 8.75 10.7625 8.225T12.1 7.7Q12.9 7.7 13.3 8.1375T13.7 9.1Q13.7 9.6 13.4 10.0375T12.65 10.85Q11.55 11.825 11.3 12.325T11.05 14.15ZM12 22Q9.925 22 8.1 21.2125T4.925 19.075Q3.575 17.725 2.7875 15.9T2 12Q2 9.925 2.7875 8.1T4.925 4.925Q6.275 3.575 8.1 2.7875T12 2Q14.075 2 15.9 2.7875T19.075 4.925Q20.425 6.275 21.2125 8.1T22 12Q22 14.075 21.2125 15.9T19.075 19.075Q17.725 20.425 15.9 21.2125T12 22ZM12 20Q15.35 20 17.675 17.675T20 12Q20 8.65 17.675 6.325T12 4Q8.65 4 6.325 6.325T4 12Q4 15.35 6.325 17.675T12 20ZM12 12Z"),
    HOME("M6 19H9V13H15V19H18V10L12 5.5 6 10V19ZM4 21V9L12 3 20 9V21H13V15H11V21H4ZM12 12.25Z"),
    HOST("M4 21Q3.175 21 2.5875 20.4125T2 19V5Q2 4.175 2.5875 3.5875T4 3H9Q9.825 3 10.4125 3.5875T11 5V19Q11 19.825 10.4125 20.4125T9 21H4ZM15 21Q14.175 21 13.5875 20.4125T13 19V5Q13 4.175 13.5875 3.5875T15 3H20Q20.825 3 21.4125 3.5875T22 5V19Q22 19.825 21.4125 20.4125T20 21H15ZM4 19H9V5H4V19ZM15 19H20V5H15V19ZM5 15H8V13H5V15ZM16 15H19V13H16V15ZM5 12H8V10H5V12ZM16 12H19V10H16V12ZM5 9H8V7H5V9ZM16 9H19V7H16V9ZM4 19H9 4ZM15 19H20 15Z"),
    INFO("M11 17H13V11H11V17ZM12 9Q12.425 9 12.7125 8.7125T13 8Q13 7.575 12.7125 7.2875T12 7Q11.575 7 11.2875 7.2875T11 8Q11 8.425 11.2875 8.7125T12 9ZM12 22Q9.925 22 8.1 21.2125T4.925 19.075Q3.575 17.725 2.7875 15.9T2 12Q2 9.925 2.7875 8.1T4.925 4.925Q6.275 3.575 8.1 2.7875T12 2Q14.075 2 15.9 2.7875T19.075 4.925Q20.425 6.275 21.2125 8.1T22 12Q22 14.075 21.2125 15.9T19.075 19.075Q17.725 20.425 15.9 21.2125T12 22ZM12 20Q15.35 20 17.675 17.675T20 12Q20 8.65 17.675 6.325T12 4Q8.65 4 6.325 6.325T4 12Q4 15.35 6.325 17.675T12 20ZM12 12Z"),
    KEYBOARD_ARROW_DOWN("M12 15.4 6 9.4 7.4 8 12 12.6 16.6 8 18 9.4 12 15.4Z"),
    KEYBOARD_ARROW_UP("M12 10.8 7.4 15.4 6 14 12 8 18 14 16.6 15.4 12 10.8Z"),
    LIST("M7 9V7H21V9H7ZM7 13V11H21V13H7ZM7 17V15H21V17H7ZM4 9Q3.575 9 3.2875 8.7125T3 8Q3 7.575 3.2875 7.2875T4 7Q4.425 7 4.7125 7.2875T5 8Q5 8.425 4.7125 8.7125T4 9ZM4 13Q3.575 13 3.2875 12.7125T3 12Q3 11.575 3.2875 11.2875T4 11Q4.425 11 4.7125 11.2875T5 12Q5 12.425 4.7125 12.7125T4 13ZM4 17Q3.575 17 3.2875 16.7125T3 16Q3 15.575 3.2875 15.2875T4 15Q4.425 15 4.7125 15.2875T5 16Q5 16.425 4.7125 16.7125T4 17Z"),
    LOCAL_CAFE("M4 21V19H20V21H4ZM8 17Q6.35 17 5.175 15.825T4 13V3H20Q20.825 3 21.4125 3.5875T22 5V8Q22 8.825 21.4125 9.4125T20 10H18V13Q18 14.65 16.825 15.825T14 17H8ZM8 15H14Q14.825 15 15.4125 14.4125T16 13V5H6V13Q6 13.825 6.5875 14.4125T8 15ZM18 8H20V5H18V8ZM8 15H6 16 8Z"),
    MICROSOFT("M2,3H11V12H2V3M11,22H2V13H11V22M21,3V12H12V3H21M21,22H12V13H21V22Z"), // TODO
    MINIMIZE("M6 21V19H18V21H6Z"),
    MOJANG("M13.9658 0C12.9552.828 12.7686 2.195 12.7007 3.418 12.7219 4.4201 14.0423 4.7174 14.6538 4.0082 15.0912 2.6579 14.3692 1.2739 13.9658 0ZM10.913 2.9297C10.7559 3.6983 10.6284 4.4669 10.4925 5.2355 8.9894 3.9913 7.1505 3.0825 5.142 3.2948 3.4944 3.4646.9429 2.6961.1404 4.6452-.1229 8.314.0722 12.0124.034 15.6896-.0891 16.9126.8957 18.1101 2.1781 17.9699 6.3989 18.0039 10.6283 18.0172 14.8491 17.9661 16.1145 18.102 16.8067 16.9554 16.9851 15.8768 13.2696 16.4374 9.1761 16.9296 5.7111 15.1292 2.5986 13.5836 2.246 8.3139 5.5581 6.798 9.3203 5.1759 13.8596 8.0383 14.9424 11.7877 15.6133 12.1105 16.2844 12.4247 16.9596 12.7432 16.6241 10.3483 16.6537 7.8005 15.5793 5.5882 15.0571 4.7474 14.0975 6.2714 13.3799 5.6217 12.416 4.8659 11.7495 3.8129 10.913 2.9297Z"), // TODO
    MORE_HORIZ("M6 14Q5.175 14 4.5875 13.4125T4 12Q4 11.175 4.5875 10.5875T6 10Q6.825 10 7.4125 10.5875T8 12Q8 12.825 7.4125 13.4125T6 14ZM12 14Q11.175 14 10.5875 13.4125T10 12Q10 11.175 10.5875 10.5875T12 10Q12.825 10 13.4125 10.5875T14 12Q14 12.825 13.4125 13.4125T12 14ZM18 14Q17.175 14 16.5875 13.4125T16 12Q16 11.175 16.5875 10.5875T18 10Q18.825 10 19.4125 10.5875T20 12Q20 12.825 19.4125 13.4125T18 14Z"),
    MORE_VERT("M12 20Q11.175 20 10.5875 19.4125T10 18Q10 17.175 10.5875 16.5875T12 16Q12.825 16 13.4125 16.5875T14 18Q14 18.825 13.4125 19.4125T12 20ZM12 14Q11.175 14 10.5875 13.4125T10 12Q10 11.175 10.5875 10.5875T12 10Q12.825 10 13.4125 10.5875T14 12Q14 12.825 13.4125 13.4125T12 14ZM12 8Q11.175 8 10.5875 7.4125T10 6Q10 5.175 10.5875 4.5875T12 4Q12.825 4 13.4125 4.5875T14 6Q14 6.825 13.4125 7.4125T12 8Z"),
    OPEN_IN_NEW("M5 21Q4.175 21 3.5875 20.4125T3 19V5Q3 4.175 3.5875 3.5875T5 3H12V5H5V19H19V12H21V19Q21 19.825 20.4125 20.4125T19 21H5ZM9.7 15.7 8.3 14.3 17.6 5H14V3H21V10H19V6.4L9.7 15.7Z"),
    PACKAGE2("M11 19.425V12.575L5 9.1V15.95L11 19.425ZM13 19.425 19 15.95V9.1L13 12.575V19.425ZM11 21.725 4 17.7Q3.525 17.425 3.2625 16.975T3 15.975V8.025Q3 7.475 3.2625 7.025T4 6.3L11 2.275Q11.475 2 12 2T13 2.275L20 6.3Q20.475 6.575 20.7375 7.025T21 8.025V15.975Q21 16.525 20.7375 16.975T20 17.7L13 21.725Q12.525 22 12 22T11 21.725ZM16 8.525 17.925 7.425 12 4 10.05 5.125 16 8.525ZM12 10.85 13.95 9.725 8.025 6.3 6.075 7.425 12 10.85Z"), // TODO
    PERSON("M12 12Q10.35 12 9.175 10.825T8 8Q8 6.35 9.175 5.175T12 4Q13.65 4 14.825 5.175T16 8Q16 9.65 14.825 10.825T12 12ZM4 20V17.2Q4 16.35 4.4375 15.6375T5.6 14.55Q7.15 13.775 8.75 13.3875T12 13Q13.65 13 15.25 13.3875T18.4 14.55Q19.125 14.925 19.5625 15.6375T20 17.2V20H4ZM6 18H18V17.2Q18 16.925 17.8625 16.7T17.5 16.35Q16.15 15.675 14.775 15.3375T12 15Q10.6 15 9.225 15.3375T6.5 16.35Q6.275 16.475 6.1375 16.7T6 17.2V18ZM12 10Q12.825 10 13.4125 9.4125T14 8Q14 7.175 13.4125 6.5875T12 6Q11.175 6 10.5875 6.5875T10 8Q10 8.825 10.5875 9.4125T12 10ZM12 8ZM12 18Z"),
    PUBLIC("M12 22Q9.925 22 8.1 21.2125T4.925 19.075Q3.575 17.725 2.7875 15.9T2 12Q2 9.925 2.7875 8.1T4.925 4.925Q6.275 3.575 8.1 2.7875T12 2Q14.075 2 15.9 2.7875T19.075 4.925Q20.425 6.275 21.2125 8.1T22 12Q22 14.075 21.2125 15.9T19.075 19.075Q17.725 20.425 15.9 21.2125T12 22ZM11 19.95V18Q10.175 18 9.5875 17.4125T9 16V15L4.2 10.2Q4.125 10.65 4.0625 11.1T4 12Q4 15.025 5.9875 17.3T11 19.95ZM17.9 17.4Q18.925 16.275 19.4625 14.8875T20 12Q20 9.55 18.6375 7.525T15 4.6V5Q15 5.825 14.4125 6.4125T13 7H11V9Q11 9.425 10.7125 9.7125T10 10H8V12H14Q14.425 12 14.7125 12.2875T15 13V16H16Q16.65 16 17.175 16.3875T17.9 17.4Z"),
    REFRESH("M12 20Q8.65 20 6.325 17.675T4 12Q4 8.65 6.325 6.325T12 4Q13.725 4 15.3 4.7125T18 6.75V4H20V11H13V9H17.2Q16.4 7.6 15.0125 6.8T12 6Q9.5 6 7.75 7.75T6 12Q6 14.5 7.75 16.25T12 18Q13.925 18 15.475 16.9T17.65 14H19.75Q19.05 16.65 16.9 18.325T12 20Z"),
    RELEASE_CIRCLE("M9,7H13A2,2 0 0,1 15,9V11C15,11.84 14.5,12.55 13.76,12.85L15,17H13L11.8,13H11V17H9V7M11,9V11H13V9H11M12,2A10,10 0 0,1 22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 12,2M12,4A8,8 0 0,0 4,12C4,16.41 7.58,20 12,20A8,8 0 0,0 20,12A8,8 0 0,0 12,4Z"), // TODO
    RESTORE("M13,3A9,9 0 0,0 4,12H1L4.89,15.89L4.96,16.03L9,12H6A7,7 0 0,1 13,5A7,7 0 0,1 20,12A7,7 0 0,1 13,19C11.07,19 9.32,18.21 8.06,16.94L6.64,18.36C8.27,20 10.5,21 13,21A9,9 0 0,0 22,12A9,9 0 0,0 13,3Z"), // TODO
    ROCKET_LAUNCH("M5.65 10.025 7.6 10.85Q7.95 10.15 8.325 9.5T9.15 8.2L7.75 7.925 5.65 10.025ZM9.2 12.1 12.05 14.925Q13.1 14.525 14.3 13.7T16.55 11.825Q18.3 10.075 19.2875 7.9375T20.15 4Q18.35 3.875 16.2 4.8625T12.3 7.6Q11.25 8.65 10.425 9.85T9.2 12.1ZM13.65 10.475Q13.075 9.9 13.075 9.0625T13.65 7.65Q14.225 7.075 15.075 7.075T16.5 7.65Q17.075 8.225 17.075 9.0625T16.5 10.475Q15.925 11.05 15.075 11.05T13.65 10.475ZM14.125 18.5 16.225 16.4 15.95 15Q15.3 15.45 14.65 15.8125T13.3 16.525L14.125 18.5ZM21.95 2.175Q22.425 5.2 21.3625 8.0625T17.7 13.525L18.2 16Q18.3 16.5 18.15 16.975T17.65 17.8L13.45 22 11.35 17.075 7.075 12.8 2.15 10.7 6.325 6.5Q6.675 6.15 7.1625 6T8.15 5.95L10.625 6.45Q13.225 3.85 16.075 2.775T21.95 2.175ZM3.925 15.975Q4.8 15.1 6.0625 15.0875T8.2 15.95Q9.075 16.825 9.0625 18.0875T8.175 20.225Q7.55 20.85 6.0875 21.3T2.05 22.1Q2.4 19.525 2.85 18.0625T3.925 15.975ZM5.35 17.375Q5.1 17.625 4.85 18.2875T4.5 19.625Q5.175 19.525 5.8375 19.2875T6.75 18.8Q7.05 18.5 7.075 18.075T6.8 17.35Q6.5 17.05 6.075 17.0625T5.35 17.375Z"),
    SCREENSHOT_MONITOR("M15 16H19V12H17.5V14.5H15V16ZM5 10H6.5V7.5H9V6H5V10ZM8 21V19H4Q3.175 19 2.5875 18.4125T2 17V5Q2 4.175 2.5875 3.5875T4 3H20Q20.825 3 21.4125 3.5875T22 5V17Q22 17.825 21.4125 18.4125T20 19H16V21H8ZM4 17H20V5H4V17ZM4 17V5 17Z"),
    SCRIPT("M14,20A2,2 0 0,0 16,18V5H9A1,1 0 0,0 8,6V16H5V5A3,3 0 0,1 8,2H19A3,3 0 0,1 22,5V6H18V18L18,19A3,3 0 0,1 15,22H5A3,3 0 0,1 2,19V18H12A2,2 0 0,0 14,20Z"), // TODO
    SEARCH("M19.6 21 13.3 14.7Q12.55 15.3 11.575 15.65T9.5 16Q6.775 16 4.8875 14.1125T3 9.5Q3 6.775 4.8875 4.8875T9.5 3Q12.225 3 14.1125 4.8875T16 9.5Q16 10.6 15.65 11.575T14.7 13.3L21 19.6 19.6 21ZM9.5 14Q11.375 14 12.6875 12.6875T14 9.5Q14 7.625 12.6875 6.3125T9.5 5Q7.625 5 6.3125 6.3125T5 9.5Q5 11.375 6.3125 12.6875T9.5 14Z"),
    SELECT_ALL("M7 17V7H17V17H7ZM9 15H15V9H9V15ZM5 19V21Q4.175 21 3.5875 20.4125T3 19H5ZM3 17V15H5V17H3ZM3 13V11H5V13H3ZM3 9V7H5V9H3ZM5 5H3Q3 4.175 3.5875 3.5875T5 3V5ZM7 21V19H9V21H7ZM7 5V3H9V5H7ZM11 21V19H13V21H11ZM11 5V3H13V5H11ZM15 21V19H17V21H15ZM15 5V3H17V5H15ZM19 21V19H21Q21 19.825 20.4125 20.4125T19 21ZM19 17V15H21V17H19ZM19 13V11H21V13H19ZM19 9V7H21V9H19ZM19 5V3Q19.825 3 20.4125 3.5875T21 5H19Z"),
    SETTINGS("M9.25 22 8.85 18.8Q8.525 18.675 8.2375 18.5T7.675 18.125L4.7 19.375 1.95 14.625 4.525 12.675Q4.5 12.5 4.5 12.3375V11.6625Q4.5 11.5 4.525 11.325L1.95 9.375 4.7 4.625 7.675 5.875Q7.95 5.675 8.25 5.5T8.85 5.2L9.25 2H14.75L15.15 5.2Q15.475 5.325 15.7625 5.5T16.325 5.875L19.3 4.625 22.05 9.375 19.475 11.325Q19.5 11.5 19.5 11.6625V12.3375Q19.5 12.5 19.45 12.675L22.025 14.625 19.275 19.375 16.325 18.125Q16.05 18.325 15.75 18.5T15.15 18.8L14.75 22H9.25ZM11 20H12.975L13.325 17.35Q14.1 17.15 14.7625 16.7625T15.975 15.825L18.45 16.85 19.425 15.15 17.275 13.525Q17.4 13.175 17.45 12.7875T17.5 12Q17.5 11.6 17.45 11.2125T17.275 10.475L19.425 8.85 18.45 7.15 15.975 8.2Q15.425 7.625 14.7625 7.2375T13.325 6.65L13 4H11.025L10.675 6.65Q9.9 6.85 9.2375 7.2375T8.025 8.175L5.55 7.15 4.575 8.85 6.725 10.45Q6.6 10.825 6.55 11.2T6.5 12Q6.5 12.4 6.55 12.775T6.725 13.525L4.575 15.15 5.55 16.85 8.025 15.8Q8.575 16.375 9.2375 16.7625T10.675 17.35L11 20ZM12.05 15.5Q13.5 15.5 14.525 14.475T15.55 12Q15.55 10.55 14.525 9.525T12.05 8.5Q10.575 8.5 9.5625 9.525T8.55 12Q8.55 13.45 9.5625 14.475T12.05 15.5ZM12 12Z"),
    STYLE("M3.975 19.8 3.125 19.45Q2.35 19.125 2.0875 18.325T2.175 16.75L3.975 12.85V19.8ZM7.975 22Q7.15 22 6.5625 21.4125T5.975 20V14L8.625 21.35Q8.7 21.525 8.775 21.6875T8.975 22H7.975ZM13.125 21.9Q12.325 22.2 11.575 21.825T10.525 20.65L6.075 8.45Q5.775 7.65 6.125 6.8875T7.275 5.85L14.825 3.1Q15.625 2.8 16.375 3.175T17.425 4.35L21.875 16.55Q22.175 17.35 21.825 18.1125T20.675 19.15L13.125 21.9ZM10.975 10Q11.4 10 11.6875 9.7125T11.975 9Q11.975 8.575 11.6875 8.2875T10.975 8Q10.55 8 10.2625 8.2875T9.975 9Q9.975 9.425 10.2625 9.7125T10.975 10ZM12.425 20 19.975 17.25 15.525 5 7.975 7.75 12.425 20ZM7.975 7.75 15.525 5 7.975 7.75Z"),
    TEXTURE("M4.4-3Q3.925-3.1 3.5125-3.5125T3-4.4L19.6-21Q20.125-20.875 20.5-20.4875T21.025-19.6L4.4-3ZM3-9.3V-12.1L11.9-21H14.7L3-9.3ZM3-17V-19Q3-19.825 3.5875-20.4125T5-21H7L3-17ZM17-3 21-7V-5Q21-4.175 20.4125-3.5875T19-3H17ZM9.3-3 21-14.7V-11.9L12.1-3H9.3Z"),
    UPDATE("M12 21Q10.125 21 8.4875 20.2875T5.6375 18.3625Q4.425 17.15 3.7125 15.5125T3 12Q3 10.125 3.7125 8.4875T5.6375 5.6375Q6.85 4.425 8.4875 3.7125T12 3Q14.05 3 15.8875 3.875T19 6.35V4H21V10H15V8H17.75Q16.725 6.6 15.225 5.8T12 5Q9.075 5 7.0375 7.0375T5 12Q5 14.925 7.0375 16.9625T12 19Q14.625 19 16.5875 17.3T18.9 13H20.95Q20.575 16.425 18.0125 18.7125T12 21ZM14.8 16.2 11 12.4V7H13V11.6L16.2 14.8 14.8 16.2Z"),
    WARNING("M1 21 12 2 23 21H1ZM4.45 19H19.55L12 6 4.45 19ZM12 18Q12.425 18 12.7125 17.7125T13 17Q13 16.575 12.7125 16.2875T12 16Q11.575 16 11.2875 16.2875T11 17Q11 17.425 11.2875 17.7125T12 18ZM11 15H13V10H11V15ZM12 12.5Z"),
    WRENCH("M22.61,19L13.53,9.91C14.46,7.57 14,4.81 12.09,2.91C9.79,0.61 6.21,0.4 3.66,2.26L7.5,6.11L6.08,7.5L2.25,3.69C0.39,6.23 0.6,9.82 2.9,12.11C4.76,13.97 7.47,14.46 9.79,13.59L18.9,22.7C19.29,23.09 19.92,23.09 20.31,22.7L22.61,20.4C23,20 23,19.39 22.61,19M19.61,20.59L10.15,11.13C9.54,11.58 8.86,11.85 8.15,11.95C6.79,12.15 5.36,11.74 4.32,10.7C3.37,9.76 2.93,8.5 3,7.26L6.09,10.35L10.33,6.11L7.24,3C8.5,2.95 9.73,3.39 10.68,4.33C11.76,5.41 12.17,6.9 11.92,8.29C11.8,9 11.5,9.66 11.04,10.25L20.5,19.7L19.61,20.59Z") // TODO
    ;

    private final String path;

    SVG(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    private static Node createIcon(SVGPath path, double width, double height) {
        if (width < 0 || height < 0) {
            StackPane pane = new StackPane(path);
            pane.setAlignment(Pos.CENTER);
            return pane;
        }

        Group svg = new Group(path);
        double scale = Math.min(width / 24, height / 24);
        svg.setScaleX(scale);
        svg.setScaleY(scale);

        return svg;
    }

    public Node createIcon(ObservableValue<? extends Paint> fill, double width, double height) {
        SVGPath p = new SVGPath();
        p.getStyleClass().add("svg");
        p.setContent(path);
        if (fill != null)
            p.fillProperty().bind(fill);

        return createIcon(p, width, height);
    }

    public Node createIcon(Paint fill, double width, double height) {
        SVGPath p = new SVGPath();
        p.getStyleClass().add("svg");
        p.setContent(path);
        if (fill != null)
            p.fillProperty().set(fill);

        return createIcon(p, width, height);
    }

}
