package org.taxireferral.api.RESTEndpointRoles;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import net.coobird.thumbnailator.Thumbnails;
import net.sargue.mailgun.Mail;
import org.taxireferral.api.DAORoles.DAOUser;
import org.taxireferral.api.Globals.GlobalConstants;
import org.taxireferral.api.Globals.Globals;
import org.taxireferral.api.ModelRoles.User;
import org.taxireferral.api.WebSocket.SimpleServer;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Collections;
import java.util.StringTokenizer;

/**
 * Created by sumeet on 2/4/17.
 */


@Path("/api/v1/User")
public class UserResource {

    // insert and sign up
    // update profile
    // check username exist
    // login
    // verify email
    // verify phone

    // Image_old Methods
    //

    private DAOUser daoUser = Globals.daoUser;


    @POST
    @Path("/DriverRegistration")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response insertItem(User user)
    {
        if(user==null)
        {
            throw new WebApplicationException();
        }


        user.setRole(GlobalConstants.ROLE_DRIVER_CODE);
        int idOfInsertedRow = daoUser.insertUser(user,false);

        user.setUserID(idOfInsertedRow);


        if(idOfInsertedRow >=1)
        {

            return Response.status(Response.Status.CREATED)
                    .entity(user)
                    .build();

        }else if(idOfInsertedRow <= 0)
        {

            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }


        return null;
    }







    @POST
    @Path("/DriverRegistrationGoogle/{IDToken}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes({MediaType.APPLICATION_JSON})
    public Response googleSignIn(@PathParam("IDToken")String idTokenString)
    {
        int idOfInsertedRow;

        System.out.println("Google ID Token" + idTokenString);

        HttpTransport httpTransport = new NetHttpTransport();
        JsonFactory jsonFactory = new JacksonFactory();

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(httpTransport,jsonFactory)
                .setAudience(Collections.singletonList("128070485514-t0rt5t6at3j888cuclgi1lj1t6a0ds99.apps.googleusercontent.com"))
                // Or, if multiple clients access the backend:
                //.setAudience(Arrays.asList(CLIENT_ID_1, CLIENT_ID_2, CLIENT_ID_3))
                .build();



        GoogleIdToken idToken = null;

        try {


            idToken = verifier.verify(idTokenString);



        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String userId  = "";
        String email = "";
        String name = "";


        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();

            // Print user identifier
            userId = payload.getSubject();
            System.out.println("User ID: " + userId);

            // Get profile information from payload
            email = payload.getEmail();
            boolean emailVerified = Boolean.valueOf(payload.getEmailVerified());
            name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");
            String locale = (String) payload.get("locale");
            String familyName = (String) payload.get("family_name");
            String givenName = (String) payload.get("given_name");

            // Use or store profile information
            // ...


            System.out.println("Email : " + email
                    + "\nName : " + name);


        }
        else
        {
            System.out.println("Invalid ID token.");

            return Response.status(Response.Status.PROXY_AUTHENTICATION_REQUIRED)
                    .build();
        }




        User userChecked = daoUser.checkGoogleID(userId);

        String token = new BigInteger(130, Globals.random).toString(32);
        Timestamp timestampExpiry = new Timestamp(System.currentTimeMillis() + GlobalConstants.TOKEN_DURATION_MINUTES*60*1000);




//        boolean userCreated = false;

        if(userChecked==null)
        {
            // create new user

            userChecked = new User();

            userChecked.setUsername(email);
            userChecked.setGoogleID(userId);
            userChecked.setName(name);
            userChecked.setEmail(email);
//            userChecked.setEmailVerified(true);
            userChecked.setRole(GlobalConstants.ROLE_DRIVER_CODE);


            SecureRandom random = new SecureRandom();
            String password = new BigInteger(30, random).toString(32);

            userChecked.setPassword(password);
            userChecked.setToken(token);
            userChecked.setTimestampTokenExpires(timestampExpiry);


            // updates the token also
            idOfInsertedRow  = daoUser.saveGoogleProfile(userChecked,false);

            if(idOfInsertedRow==-1)
            {
//                userChecked = null;


                return Response.status(Response.Status.NOT_MODIFIED)
                        .build();
            }
            else
            {
                userChecked.setUserID(idOfInsertedRow);

                // send an email notifying the user about new account creation and also giving him the password
                Mail.using(Globals.configurationMailgun)
                        .body()
                        .h1("Taxi Referral : Account Registered via Google Profile")
                        .p("Your account has been Created.")
                        .h3("Your Username is : " + userChecked.getUsername())
                        .h3("Your Password (auto-generated) is  : " + userChecked.getPassword())
                        .p("You can login via google account or also using your username and password. In case login via google Account does not work you can use the username and password provided here to login directly to your account. Please store these credentials in the safe place because you may need them in future. ")
                        .mail()
                        .to(userChecked.getEmail())
                        .subject("Taxi Referral Service : Account registered via Google Profile")
                        .from("Taxi Referral Service","noreply@taxireferral.org")
                        .build()
                        .send();



                return Response.status(Response.Status.CREATED)
                        .entity(userChecked)
                        .build();
            }


        }
        else
        {

            userChecked.setToken(token);
            userChecked.setTimestampTokenExpires(timestampExpiry);

            // update the new token
            daoUser.updateToken(userChecked);


            if(SimpleServer.connection!=null && SimpleServer.connection.isOpen())
            {
                SimpleServer.connection.send("Logged IN View Google Account !");
            }

            Globals.sendSms("Your Verification code is : aioib3");

            return Response.status(Response.Status.OK)
                    .entity(userChecked)
                    .build();
        }

    }





//    GlobalConstants.ROLE_ADMIN,GlobalConstants.ROLE_STAFF,GlobalConstants.ROLE_END_USER

    @PUT
    @Path("/{UserID}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({GlobalConstants.ROLE_DRIVER})
    public Response updateDriver(User user, @PathParam("UserID")int userID)
    {

        user.setUserID(userID);
        int rowCount = daoUser.updateUser(user);


        if(rowCount >= 1)
        {


//            if(SimpleServer.connection!=null)
//            {
//                SimpleServer.connection.send("Driver Updated !");
//                SimpleServer.connection.close();
//            }



            return Response.status(Response.Status.OK)
                    .build();
        }
        if(rowCount == 0)
        {

            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }

        return null;
    }


    @PUT
    @Path("/ChangePassword/{OldPassword}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({GlobalConstants.ROLE_DRIVER})
    public Response changePassword(User user, @PathParam("OldPassword")String oldPassword)
    {

        int rowCount = daoUser.updatePassword(user,oldPassword);


        if(rowCount >= 1)
        {
            return Response.status(Response.Status.OK)
                    .build();
        }
        if(rowCount == 0)
        {

            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }

        return null;
    }



    @PUT
    @Path("/ChangeEmail/{NewEmail}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({GlobalConstants.ROLE_DRIVER})
    public Response changeEmail(@PathParam("NewEmail")String newEmail)
    {

        User user = (User) Globals.accountApproved;
        user.setEmail(newEmail);



        int rowCount = daoUser.updateEmail(user);


        if(rowCount >= 1)
        {
            return Response.status(Response.Status.OK)
                    .build();
        }
        if(rowCount == 0)
        {

            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }

        return null;
    }





    @PUT
    @Path("/SendEmailVerificationCode")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({GlobalConstants.ROLE_DRIVER})
    public Response sendVerificationEmail()
    {

        User user = (User) Globals.accountApproved;
        int rowCount = 0;

        System.out.println("Username : " + user.getUsername());

        User userTemp = daoUser.checkEmailCode(user.getUsername());

//        System.out.println("User TEMP : " + userTemp.getEmailVerificationCodeTimestampExpires().toLocaleString());

        Timestamp timestampExpries = userTemp.getEmailVerificationCodeTimestampExpires();

        if(timestampExpries==null || (timestampExpries!=null && timestampExpries.before(new Timestamp(System.currentTimeMillis()))))
        {
           // verification code expired so generate new code and email it to the user

            String emailVerificationCode = new BigInteger(30, Globals.random).toString(32);
            Timestamp timestampExpiry = new Timestamp(System.currentTimeMillis() + 15*60*1000);

            user.setEmailVerificationCode(emailVerificationCode);
            user.setEmailVerificationCodeTimestampExpires(timestampExpiry);


            rowCount = daoUser.setEmailVerificationCode(user);

            if(rowCount==1)
            {

                System.out.println("Email Verification Code : " + emailVerificationCode);

                Mail.using(Globals.configurationMailgun)
                        .body()
                        .h1("Your E-mail Verification Code is given below")
                        .p("You have requested to verify your e-mail. If you did not request the e-mail verification please ignore this e-mail message.")
                        .h3("The e-mail verification code is : " + emailVerificationCode)
                        .p("Please use this code within 15 minutes because it will expire after 15 minutes.")
                        .mail()
                        .to(userTemp.getEmail())
                        .subject("E-mail Verification Code for Taxi Referral Service (TRS)")
                        .from("Taxi Referral Service","noreply@taxireferral.org")
                        .build()
                        .send();
            }

        }
        else
        {
            // the existing code has not expired to email the existing code

            System.out.println("Email Verification Code (Resent): " + userTemp.getEmailVerificationCode());

            Mail.using(Globals.configurationMailgun)
                    .body()
                    .h1("Your E-mail Verification Code is given below")
                    .p("You have requested to verify your e-mail. If you did not request the e-mail verification please ignore this e-mail message.")
                    .h3("The e-mail verification code is : " + userTemp.getEmailVerificationCode())
                    .p("Please use this code within 15 minutes because it will expire after 15 minutes.")
                    .mail()
                    .to(userTemp.getEmail())
                    .subject("E-mail Verification Code for Taxi Referral : Resend Request")
                    .from("Taxi Referral Service","noreply@taxireferral.org")
                    .build()
                    .send();


            rowCount = 1;
        }




        if(rowCount >= 1)
        {



            return Response.status(Response.Status.OK)
                    .build();
        }
        if(rowCount == 0)
        {

            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }

        return null;
    }




    @PUT
    @Path("/VerifyEmail/{VerificationCode}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({GlobalConstants.ROLE_DRIVER})
    public Response verifyEmail(@PathParam("VerificationCode")String verificationCode)
    {

        User user = (User) Globals.accountApproved;
        user.setEmailVerificationCode(verificationCode);

        int rowCount = daoUser.setEmailVerified(user);


        if(rowCount >= 1)
        {
            return Response.status(Response.Status.OK)
                    .build();
        }
        if(rowCount == 0)
        {

            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }

        return null;
    }





//    @GET
//    public Response getSample()
//    {
//
//        System.out.println("Get method !");
//
//        return Response.status(Response.Status.OK)
//                .entity(new String("Hello this is a test !"))
//                .build();
//
//    }






    @GET
    @Path("/CheckUsernameExists/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkUsername(@PathParam("username")String username)
    {
        // Roles allowed not used for this method due to performance and effeciency requirements. Also
        // this endpoint doesnt required to be secured as it does not expose any confidential information

        boolean result = daoUser.checkUsernameExists(username);

        if(result)
        {
            return Response.status(Response.Status.OK)
                    .build();

        } else
        {
            return Response.status(Response.Status.NO_CONTENT)
                    .build();
        }

    }


//    @RolesAllowed({GlobalConstants.ROLE_ADMIN,GlobalConstants.ROLE_STAFF,GlobalConstants.ROLE_DRIVER,GlobalConstants.ROLE_END_USER})



    @GET
    @Path("/GetProfile")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProfile(@HeaderParam("Authorization")String headerParam)
    {


        final String encodedUserPassword = headerParam.replaceFirst(AUTHENTICATION_SCHEME + " ", "");

        //Decode username and password
        String usernameAndPassword = new String(Base64.getDecoder().decode(encodedUserPassword.getBytes()));

        //Split username and password tokens
        final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
        final String username = tokenizer.nextToken();
        final String password = tokenizer.nextToken();

        //Verifying Username and password
        System.out.println(username);
        System.out.println(password);



        String token = new BigInteger(130, Globals.random).toString(32);
        Timestamp timestampExpiry = new Timestamp(System.currentTimeMillis() + 1*3*60*1000);

        User user = daoUser.getProfile(username,password);



        if(user!=null)
        {

            user.setToken(token);
            user.setTimestampTokenExpires(timestampExpiry);

            // password is required for updating the token
            user.setPassword(password);

            int rowsUpdated = daoUser.updateToken(user);

            // we choose not to send password over the wire for security reasons
            user.setPassword(null);

            if(rowsUpdated==1)
            {

                return Response.status(Response.Status.OK)
                        .entity(user)
                        .build();
            }
            else
            {
                return Response.status(Response.Status.NOT_MODIFIED)
                        .build();
            }


        }
        else
        {
            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }

    }







    private static final String AUTHENTICATION_SCHEME = "Basic";


    @GET
    @Path("/GetToken")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getToken(@HeaderParam("Authorization")String headerParam)
    {

//        @Context HttpHeaders headers

//        List<String> headerString = headers.getRequestHeader(HttpHeaders.AUTHORIZATION);
//
//        String headerFull = "";
//
//        for(String str : headerString)
//        {
//            headerFull = headerFull + " : " +  str;
//        }


        final String encodedUserPassword = headerParam.replaceFirst(AUTHENTICATION_SCHEME + " ", "");

        //Decode username and password
        String usernameAndPassword = new String(Base64.getDecoder().decode(encodedUserPassword.getBytes()));
        System.out.println("Username:Password" + usernameAndPassword);

        //Split username and password tokens
        final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
        final String username = tokenizer.nextToken();
        final String password = tokenizer.nextToken();

        //Verifying Username and password
        System.out.println(username);
        System.out.println(password);



        String token = new BigInteger(130, Globals.random).toString(32);

        Timestamp timestampExpiry = new Timestamp(System.currentTimeMillis() + 1*3*60*1000);


//        Date dt = new Date();
//        DateTime dtOrg = new DateTime(System.currentTimeMillis());
//        DateTime dtPlusOne = dtOrg.plusDays(1);
//        timestamp  = new Timestamp(dtPlusOne.getMillis());


        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setToken(token);
        user.setTimestampTokenExpires(timestampExpiry);

        int rowsUpdated = daoUser.updateToken(user);




        if(rowsUpdated==1)
        {

            return Response.status(Response.Status.OK)
                    .entity(user)
                    .build();
        }
        else if(rowsUpdated==0)
        {
            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }
        else
        {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .build();
        }

    }










    // Image_old MEthods

    private static final java.nio.file.Path BASE_DIR = Paths.get("./images/User");
    private static final double MAX_IMAGE_SIZE_MB = 2;


    @POST
    @Path("/Image_old")
    @Consumes({MediaType.APPLICATION_OCTET_STREAM})
    @RolesAllowed({GlobalConstants.ROLE_ADMIN,GlobalConstants.ROLE_DRIVER,GlobalConstants.ROLE_STAFF,GlobalConstants.ROLE_END_USER})
    public Response uploadImage(InputStream in, @HeaderParam("Content-Length") long fileSize,
                                @QueryParam("PreviousImageName") String previousImageName
    ) throws Exception
    {


        if(previousImageName!=null)
        {
            Files.deleteIfExists(BASE_DIR.resolve(previousImageName));
            Files.deleteIfExists(BASE_DIR.resolve("three_hundred_" + previousImageName + ".jpg"));
            Files.deleteIfExists(BASE_DIR.resolve("five_hundred_" + previousImageName + ".jpg"));
        }


        File theDir = new File(BASE_DIR.toString());

        // if the directory does not exist, create it
        if (!theDir.exists()) {

            System.out.println("Creating directory: " + BASE_DIR.toString());

            boolean result = false;

            try{
                theDir.mkdir();
                result = true;
            }
            catch(Exception se){
                //handle it
            }
            if(result) {
                System.out.println("DIR created");
            }
        }



        String fileName = "" + System.currentTimeMillis();

        // Copy the file to its location.
        long filesize = Files.copy(in, BASE_DIR.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        if(filesize > MAX_IMAGE_SIZE_MB * 1048 * 1024)
        {
            // delete file if it exceeds the file size limit
            Files.deleteIfExists(BASE_DIR.resolve(fileName));

            return Response.status(Response.Status.EXPECTATION_FAILED).build();
        }


        createThumbnails(fileName);


        Image_old imageOld = new Image_old();
        imageOld.setPath(fileName);

        // Return a 201 Created response with the appropriate Location header.

        return Response.status(Response.Status.CREATED).location(URI.create("/api/Images/" + fileName)).entity(imageOld).build();
    }



    private void createThumbnails(String filename)
    {
        try {

            Thumbnails.of(BASE_DIR.toString() + "/" + filename)
                    .size(300,300)
                    .outputFormat("jpg")
                    .toFile(new File(BASE_DIR.toString() + "/" + "three_hundred_" + filename));

            //.toFile(new File("five-" + filename + ".jpg"));

            //.toFiles(Rename.PREFIX_DOT_THUMBNAIL);


            Thumbnails.of(BASE_DIR.toString() + "/" + filename)
                    .size(500,500)
                    .outputFormat("jpg")
                    .toFile(new File(BASE_DIR.toString() + "/" + "five_hundred_" + filename));



        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @GET
    @Path("/Image_old/{name}")
    @Produces("image/jpeg")
    public InputStream getImage(@PathParam("name") String fileName) {

        //fileName += ".jpg";
        java.nio.file.Path dest = BASE_DIR.resolve(fileName);

        if (!Files.exists(dest)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }


        try {
            return Files.newInputStream(dest);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }



    @DELETE
    @Path("/Image_old/{name}")
    @RolesAllowed({GlobalConstants.ROLE_ADMIN,GlobalConstants.ROLE_DRIVER,GlobalConstants.ROLE_STAFF,GlobalConstants.ROLE_END_USER})
    public Response deleteImageFile(@PathParam("name")String fileName)
    {

        boolean deleteStatus = false;

        Response response;

        System.out.println("Filename: " + fileName);

        try {


            //Files.delete(BASE_DIR.resolve(fileName));
            deleteStatus = Files.deleteIfExists(BASE_DIR.resolve(fileName));

            // delete thumbnails
            Files.deleteIfExists(BASE_DIR.resolve("three_hundred_" + fileName + ".jpg"));
            Files.deleteIfExists(BASE_DIR.resolve("five_hundred_" + fileName + ".jpg"));


        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        if(!deleteStatus)
        {
            response = Response.status(Response.Status.NOT_MODIFIED).build();

        }else
        {
            response = Response.status(Response.Status.OK).build();
        }

        return response;
    }


}