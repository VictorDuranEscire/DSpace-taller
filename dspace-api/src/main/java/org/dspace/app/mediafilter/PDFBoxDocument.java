package org.dspace.app.mediafilter;

import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.storage.bitstore.DSBitStoreService;
import org.dspace.utils.DSpace;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class PDFBoxDocument extends ImageMagickThumbnailFilter{

    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(PDFBoxThumbnail.class);
    protected final List<String> publicFiltersClasses = new ArrayList<>();

    protected final BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    protected final ItemService itemService =           ContentServiceFactory.getInstance().getItemService();

    protected final DSBitStoreService dsBitStoreService =    new DSpace().getServiceManager()
                .getServicesByType(DSBitStoreService.class)
                .get(0);

    @Override
    public String getFilteredName(String sourceName) {
         return "OUTPUT.pdf";
    }

    @Override
    public String getBundleName() {
        return "ORIGINAL";
    }

    @Override
    public String getFormatString() {
        return "PDF";
    }

    @Override
    public String getDescription() {
        return "Generated Document";
    }


    @Override
    public boolean preProcessBitstream(Context c, Item item, Bitstream source, boolean verbose)  throws Exception {
        Optional<Bundle> bundle  = itemService.getBundles(item, "ORIGINAL").stream().findFirst();
        System.out.println("Procesando ....");;

        if (bundle.isEmpty()) return  false;

        Optional<Bitstream> bitstream = bundle.get().getBitstreams().stream().filter( bitstream1 -> bitstream1.getName().equals("OUTPUT.pdf") ).findFirst();
        int bitstreamSize  = (int) bundle.get().getBitstreams().stream().filter(bitstream1 -> !bitstream1.getName().equals("OUTPUT.pdf")).count();

        if(bitstream.isPresent()){
            System.out.println("Se encontro ....");;
            final InputStream inputStream = bitstreamService.retrieve(c, bitstream.get());
            try(PDDocument pdDocument = PDDocument.load(inputStream)) {
                int documentSize = pdDocument.getNumberOfPages();
                pdDocument.close();
                if(documentSize == bitstreamSize) {
                    System.out.println("El item no tiene actualizaciones disponivles");;
                    return false;
                }else {
                    System.out.println("Si paso");;
                    bitstreamService.delete(c, bitstream.get());
                }
            }
        }
        return super.preProcessBitstream(c,item,source,verbose);  //default to no pre-processing
    }

    @Override
    public InputStream getDestinationStream(Item item, InputStream source, boolean verbose) throws Exception {
        Context context = new Context();

        Optional<Bundle> bundle = item.getBundles().stream().filter( bundle1 -> bundle1.getName().contains("ORIGINAL") ).findFirst();

        if (bundle.isEmpty()) {
            throw new Exception("Could not find ORIGINAL bundle");
        }

        PDDocument document = new PDDocument();

        for (Bitstream bitstream : bundle.get().getBitstreams()) {
            try {
                final InputStream inputStream = bitstreamService.retrieve(context, bitstream);


                try {
                    PDPage page = new PDPage();
                    page.setMediaBox(PDRectangle.A4);

                    // Convertir BufferedImage a PDImageXImage
                    PDImageXObject image = PDImageXObject.createFromByteArray(document, inputStream.readAllBytes(), "image");
                    boolean isBigger = image.getWidth() >= 2482;
                    float width = PDRectangle.A4.getWidth();
                    float height = PDRectangle.A4.getHeight();
                    if(isBigger){
                        page.setMediaBox(new PDRectangle(2383.937F, 2070.3938F));
                         width =  PDRectangle.A0.getWidth();
                         height = 2070;
                    }
                    if(image.getWidth() <= 500) {
                        page.setMediaBox(new PDRectangle(image.getWidth(), image.getHeight()));
                        width =  image.getWidth();
                        height = image.getHeight();
                    }
                    // Añadir la imagen a la página
                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                        contentStream.drawImage(image, 0, 0, width,height);
                    }
                    document.addPage(page);
                } finally {
                    inputStream.close();
                }

            } catch (Exception e){
                e.printStackTrace();
            }
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            document.close();
            return new ByteArrayInputStream(out.toByteArray());
        }
    }


}
